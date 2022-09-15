package ai.datasqrl.plan.global;

import ai.datasqrl.config.AbstractDAG;
import ai.datasqrl.config.util.StreamUtil;
import ai.datasqrl.plan.calcite.OptimizationStage;
import ai.datasqrl.plan.calcite.Planner;
import ai.datasqrl.plan.calcite.hints.WatermarkHint;
import ai.datasqrl.plan.calcite.rules.DAGExpansionRule;
import ai.datasqrl.plan.calcite.table.*;
import ai.datasqrl.plan.calcite.util.CalciteUtil;
import ai.datasqrl.plan.queries.APIQuery;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelShuttleImpl;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.hint.Hintable;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.tools.Programs;
import org.apache.calcite.tools.RelBuilder;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

@AllArgsConstructor
public class DAGPlanner {

    private final Planner planner;

    public OptimizedDAG plan(CalciteSchema relSchema, Collection<APIQuery> queries) {

        List<QueryRelationalTable> queryTables = CalciteUtil.getTables(relSchema, QueryRelationalTable.class);
        Multimap<QueryRelationalTable,VirtualRelationalTable> toVirtual = HashMultimap.create();
        CalciteUtil.getTables(relSchema, VirtualRelationalTable.class).forEach(vt -> toVirtual.put(vt.getRoot().getBase(),vt));

        //1. Build the actual DAG
        LogicalDAG dag = LogicalDAG.of(queryTables, queries);
        dag = dag.trimToSinks(); //Remove unreachable parts of the DAG

        //2. Optimize each table and determine its materialization preference
        Map<StreamTableNode, MaterializationPreference> materialization = new HashMap<>();
        for (StreamTableNode tableNode : Iterables.filter(dag, StreamTableNode.class)) {
            QueryRelationalTable table = tableNode.table;
            //1. Optimize the logical plan and compute statistic
            optimizeTable(table); //TODO: should this add inlined columns to all relnodes?
            //2. Determine if we should materialize this table
            MaterializationPreference materialize = determineMaterialization(table);
            // make sure materialization strategy is compatible with inputs, else try to adjust
            Iterable<StreamTableNode> allinputs = Iterables.filter(dag.getAllInputsFromSource(tableNode, false), StreamTableNode.class);
            if (materialize == MaterializationPreference.MUST) {
                if (!Iterables.isEmpty(Iterables.filter(allinputs,t -> materialization.get(t) == MaterializationPreference.CANNOT))) {
                    throw new IllegalStateException("Incompatible materialization strategies");
                } else {
                    //Convert all inputs to "SHOULD"
                    Iterables.filter(allinputs, t-> materialization.get(t) == MaterializationPreference.SHOULD_NOT)
                            .forEach(t -> materialization.put(t, MaterializationPreference.SHOULD));
                }
            } else if (materialize == MaterializationPreference.SHOULD) {
                if (!Iterables.isEmpty(Iterables.filter(allinputs,t -> !materialization.get(t).isMaterialize()))) {
                    //At least one input should or can not be materialized, and hence neither should this table
                    materialize = MaterializationPreference.SHOULD_NOT;
                }
            }
            materialization.put(tableNode,materialize);
        }
        //3. If we don't materialize, input tables need to be persisted (i.e. determine where we cut the DAG)
        //   and if we do, then we need to add an extra DBTableNode.
        //  This determines the materialization strategy for materialized tables.
        Map<QueryRelationalTable,MaterializationStrategy> materializeStrategies = new HashMap<>();
        List<DBTableNode> nodes2Add = new ArrayList<>();
        for (StreamTableNode tableNode : Iterables.filter(dag, StreamTableNode.class)) {
//            tableNode.table.getMatStrategy().setMaterialize(materialization.get(tableNode).isMaterialize());
            if (materialization.get(tableNode).isMaterialize()) {
//                boolean isPersisted = dag.getOutputs(tableNode).stream().anyMatch(DBTableNode.class::isInstance);
                String persistedAs = null;
                //If this node is materialized but some streamtable outputs aren't (i.e. they are computed in the database)
                //we need to persist this table and set a flag to indicate how to expand this table
                if (StreamUtil.filterByClass(dag.getOutputs(tableNode).stream(),StreamTableNode.class)
                        .map(materialization::get).anyMatch(Predicate.not(MaterializationPreference::isMaterialize))) {
                    VirtualRelationalTable vtable = Iterables.getOnlyElement(toVirtual.get(tableNode.table));
                    Preconditions.checkState(vtable.isRoot());
                    nodes2Add.add(new DBTableNode(vtable));
                    persistedAs = vtable.getNameId();
                }
                materializeStrategies.put(tableNode.table, new MaterializationStrategy(persistedAs));
            }
        }
        dag = dag.addNodes(nodes2Add);

        //4. Produce an LP-tree for each persisted table
        List<OptimizedDAG.MaterializeQuery> writeDAG = new ArrayList<>();
        DAGExpansionRule.Write writeExpansion = new DAGExpansionRule.Write(materializeStrategies);
        OptimizationStage writeDAGOptimization = new OptimizationStage("WriteDAGExpansion",
                Programs.hep(List.of(writeExpansion), false, DefaultRelMetadataProvider.INSTANCE),
                Optional.empty());
        for (DBTableNode dbNode : Iterables.filter(dag, DBTableNode.class)) {
            if (materializeStrategies.containsKey(dbNode.table.getRoot().getBase())) {
                VirtualRelationalTable dbTable = dbNode.table;
                RelNode scanTable = planner.getRelBuilder().scan(dbTable.getNameId()).build();
                RelNode expanded = planner.transform(writeDAGOptimization,scanTable);
                writeDAG.add(new OptimizedDAG.MaterializeQuery(new OptimizedDAG.TableSink(dbTable),expanded));
            }
        }
        //5. Produce an LP-tree for each subscription
        //TODO: implement using OptimizedDAG.StreamSink

        //6. Produce an LP-tree for each query with all tables inlined and push down filters to determine indexes
        List<OptimizedDAG.ReadQuery> readDAG = new ArrayList<>();
        OptimizationStage readDAGOptimization = new OptimizationStage("ReadDAGExpansion",
                Programs.hep(List.of(new DAGExpansionRule.Read(materializeStrategies, writeExpansion.getPullups())),
                        false, DefaultRelMetadataProvider.INSTANCE), Optional.empty());
        for (QueryNode qNode : Iterables.filter(dag, QueryNode.class)) {
            RelNode expanded = planner.transform(readDAGOptimization,qNode.query.getRelNode());
            //TODO: Push down filters into queries to determine indexes needed on tables
            readDAG.add(new OptimizedDAG.ReadQuery(qNode.query,expanded));
        }

        return new OptimizedDAG(writeDAG,readDAG);
    }

    private void optimizeTable(QueryRelationalTable table) {
        if (table instanceof ProxyImportRelationalTable) {
            // Determine timestamp
            if (!table.getTimestamp().hasTimestamp()) {
                table.getTimestamp().setBestTimestamp();
            }
            // Rewrite LogicalValues to TableScan and add watermark hint
            new ImportTableRewriter((ProxyImportRelationalTable) table,planner.getRelBuilder()).replaceImport();
        }
        //Since we are iterating source->sink, every non-state table should have a timestamp at this point
        Preconditions.checkArgument(!table.getType().hasTimestamp() || table.getTimestamp().hasTimestamp());
        //TODO: run volcano optimizer and get row estimate
        RelNode optimizedRel = table.getRelNode();
        table.updateRelNode(optimizedRel);
        table.setStatistic(TableStatistic.of(1));
    }

    private MaterializationPreference determineMaterialization(QueryRelationalTable table) {
        //TODO: implement based on following criteria:
        //- if imported table => MUST
        //- if subscription => MUST
        //- if hint provided => MUST or CANNOT depending on hint
        //- nested structure => MUST
        //- contains function that cannot be executed in database => MUST
        //- contains inner join where one side is high cardinality (with configurable threshold) => SHOULD NOT
        //- else SHOULD
        if (table instanceof ProxyImportRelationalTable) return MaterializationPreference.MUST;
        if (CalciteUtil.hasNesting(table.getRowType())) return MaterializationPreference.MUST;
        return MaterializationPreference.SHOULD;
    }

    private interface DAGNode extends AbstractDAG.Node {

        Stream<DAGNode> getInputs();

        default StreamTableNode asTable() {
            return null;
        }

    }

    @Value
    private static class StreamTableNode implements DAGNode {

        private final QueryRelationalTable table;

        private StreamTableNode(QueryRelationalTable table) {
            this.table = table;
        }

        @Override
        public Stream<DAGNode> getInputs() {
            if (table instanceof ProxyImportRelationalTable) return Stream.empty(); //imported tables have no inputs
            return VisitTableScans.findScanTables(table.getRelNode()).stream()
                    .map(t -> new StreamTableNode((QueryRelationalTable) t));
        }

        @Override
        public StreamTableNode asTable() {
            return this;
        }

        @Override
        public boolean isSink() {
            //TODO: return true if table is subscription
            return false;
        }
    }

    @Value
    private static class DBTableNode implements DAGNode {

        VirtualRelationalTable table;

        @Override
        public Stream<DAGNode> getInputs() {
            return Stream.of(new StreamTableNode(table.getRoot().getBase()));
        }
    }

    @Value
    private static class QueryNode implements DAGNode {

        private final APIQuery query;

        @Override
        public Stream<DAGNode> getInputs() {
            return VisitTableScans.findScanTables(query.getRelNode()).stream()
                    .map(t -> new DBTableNode((VirtualRelationalTable) t));
        }

        @Override
        public boolean isSink() {
            return true;
        }
    }

    private static class LogicalDAG extends AbstractDAG<DAGNode, LogicalDAG> {

        protected LogicalDAG(Multimap<DAGNode, DAGNode> inputs) {
            super(inputs);
        }

        @Override
        protected LogicalDAG create(Multimap<DAGNode, DAGNode> inputs) {
            return new LogicalDAG(inputs);
        }

        public static LogicalDAG of(List<QueryRelationalTable> queryTables, Collection<APIQuery> queries) {
            Multimap<DAGNode, DAGNode> inputs = toInputs(queryTables.stream().map(t -> new StreamTableNode(t)));
            inputs.putAll(toInputs(queries.stream().map(q -> new QueryNode(q)).flatMap(qn -> Stream.concat(Stream.of(qn),qn.getInputs()))));
            return new LogicalDAG(inputs);
        }

        private static Multimap<DAGNode, DAGNode> toInputs(Stream<? extends DAGNode> nodes) {
            Multimap<DAGNode, DAGNode> inputs = HashMultimap.create();
            nodes.forEach( node -> {
                node.getInputs().forEach(input -> inputs.put(node,input));
            });
            return inputs;
        }

        public LogicalDAG addNodes(Collection<? extends DAGNode> nodes) {
            return addNodes(toInputs(nodes.stream()));
        }
    }


    private static class VisitTableScans extends RelShuttleImpl {

        final Set<AbstractRelationalTable> scanTables = new HashSet<>();

        public static Set<AbstractRelationalTable> findScanTables(@NonNull RelNode relNode) {
            VisitTableScans vts = new VisitTableScans();
            relNode.accept(vts);
            return vts.scanTables;
        }

        @Override
        public RelNode visit(TableScan scan) {
            QueryRelationalTable table = scan.getTable().unwrap(QueryRelationalTable.class);
            if (table==null) { //It's a database query
                scanTables.add(scan.getTable().unwrap(VirtualRelationalTable.class));
            } else {
                scanTables.add(table);
            }
            return super.visit(scan);
        }
    }

    @AllArgsConstructor
    private static class ImportTableRewriter extends RelShuttleImpl {

        final ProxyImportRelationalTable table;
        final RelBuilder relBuilder;

        public void replaceImport() {
            RelNode updated = table.getRelNode().accept(this);
            int timestampIdx = table.getTimestamp().getTimestampIndex();
            Preconditions.checkArgument(timestampIdx<updated.getRowType().getFieldCount());
            WatermarkHint watermarkHint = new WatermarkHint(timestampIdx);
            updated = ((Hintable)updated).attachHints(List.of(watermarkHint.getHint()));
            table.updateRelNode(table.getRelNode().accept(this));
        }

        @Override
        public RelNode visit(LogicalValues values) {
            //The Values are a place-holder for the tablescan
            return relBuilder.scan(table.getSourceTable().getNameId()).build();
        }
    }

}