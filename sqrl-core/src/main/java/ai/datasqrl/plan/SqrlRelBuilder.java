package ai.datasqrl.plan;

import ai.datasqrl.schema.Table;
import ai.datasqrl.validate.imports.ImportManager.SourceTableImport;
import ai.datasqrl.plan.nodes.ShredTableScan;
import ai.datasqrl.plan.nodes.StreamTableScan;
import java.util.List;
import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.tools.RelBuilder;

public class SqrlRelBuilder extends RelBuilder {

  public SqrlRelBuilder(Context context,
      RelOptCluster cluster,
      RelOptSchema relOptSchema) {
    super(context, cluster, relOptSchema);
  }

  public SqrlRelBuilder scanStream(String name, SourceTableImport sourceTable,
      Table sqrlTable) {
    RelOptTable table = relOptSchema.getTableForMember(List.of(name));
    StreamTableScan scan = new StreamTableScan(this.cluster, RelTraitSet.createEmpty(), List.of(), table, sourceTable, sqrlTable);

    this.push(scan);
    return this;
  }

  public SqrlRelBuilder scanShred(Table fromTable, String name) {
    RelOptTable table = relOptSchema.getTableForMember(List.of(name));
    ShredTableScan scan = new ShredTableScan(this.cluster, RelTraitSet.createEmpty(), List.of(), table, fromTable);

    this.push(scan);
    return this;
  }
}