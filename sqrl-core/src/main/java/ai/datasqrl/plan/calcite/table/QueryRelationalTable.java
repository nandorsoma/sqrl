package ai.datasqrl.plan.calcite.table;

import ai.datasqrl.parse.tree.name.Name;
import ai.datasqrl.plan.calcite.util.CalciteUtil;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A relational table that is defined by the user query in the SQRL script.
 *
 * This is a physical relation that gets materialized in the write DAG or computed in the read DAG.
 */
@Getter
public class QueryRelationalTable extends AbstractRelationalTable {

  @NonNull
  private final TableType type;
  @NonNull
  private final TimestampHolder.Base timestamp;
  @NonNull
  private final int numPrimaryKeys;

  protected RelNode relNode;
  /* Additional operators at the root of the relNode logical plan that we want to pull-up as much as possible
  and execute in the database because they are expensive or impossible to execute in a stream
   */
  private final PullupOperator.Container pullups;

  @Setter
  private TableStatistic statistic = null;


  public QueryRelationalTable(@NonNull Name rootTableId, @NonNull TableType type,
                              RelNode relNode, PullupOperator.Container pullups,
                              @NonNull TimestampHolder.Base timestamp,
                              @NonNull int numPrimaryKeys) {
    super(rootTableId);
    this.type = type;
    this.timestamp = timestamp;
    this.relNode = relNode;
    this.pullups = pullups;
    this.numPrimaryKeys = numPrimaryKeys;
  }

  public RelNode getRelNode() {
    Preconditions.checkState(relNode!=null,"Not yet initialized");
    return relNode;
  }

  public void updateRelNode(@NonNull RelNode relNode) {
    this.relNode = relNode;
  }

  public void addInlinedColumn(AddedColumn.Simple column, Supplier<RelBuilder> relBuilderFactory,
                               Optional<Integer> timestampScore) {
    this.relNode = column.appendTo(relBuilderFactory.get().push(relNode)).build();
    //Check if this adds a timestamp candidate
    if (timestampScore.isPresent() && !timestamp.isCandidatesLocked()) {
      int index = relNode.getRowType().getFieldCount()-1; //Index of the field we just added
      timestamp.addCandidate(index, timestampScore.get());
    }
  }

  private static RelDataTypeField getField(FieldIndexPath path, RelDataType rowType) {
    Preconditions.checkArgument(path.size() > 0);
    Preconditions.checkArgument(rowType.isStruct(), "Expected relational data type but found: %s",
        rowType);
    int firstIndex = path.get(0);
    Preconditions.checkArgument(firstIndex < rowType.getFieldCount());
    RelDataTypeField field = rowType.getFieldList().get(firstIndex);
    path = path.popFirst();
    if (path.isEmpty()) {
      return field;
    } else {
      return getField(path, field.getType());
    }
  }

  @Override
  public RelDataType getRowType() {
    return relNode.getRowType();
  }

  public boolean isPrimaryKey(FieldIndexPath path) {
    Preconditions.checkArgument(path.size() > 0);
    if (path.size() == 1) {
      return path.get(0) < numPrimaryKeys;
    } else {
      if (path.getLast() != 0) {
        return false;
      }
      RelDataType type = getField(path.popLast()).getType();
      return CalciteUtil.isNestedTable(type) && CalciteUtil.isArray(type);
    }
  }

  public RelDataTypeField getField(FieldIndexPath path) {
    return getField(path, getRowType());
  }

  @Override
  public Statistic getStatistic() {
    if (statistic != null) {
      return Statistics.of(statistic.getRowCount(), List.of(ImmutableBitSet.of(numPrimaryKeys)));
    } else {
      return Statistics.UNKNOWN;
    }
  }


  @Override
  public List<String> getPrimaryKeyNames() {
    throw new UnsupportedOperationException();
  }
}