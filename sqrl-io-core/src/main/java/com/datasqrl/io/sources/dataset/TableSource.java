package com.datasqrl.io.sources.dataset;

import com.datasqrl.io.sources.DataSystemConnector;
import com.datasqrl.io.sources.stats.TableStatistic;
import com.datasqrl.parse.tree.name.Name;
import com.datasqrl.parse.tree.name.NamePath;
import com.datasqrl.schema.input.FlexibleDatasetSchema;
import com.datasqrl.schema.input.InputTableSchema;
import lombok.Getter;
import lombok.NonNull;

/**
 * A {@link TableSource} defines an input source to be imported into an SQML script. A {@link
 * TableSource} is comprised of records and is the smallest unit of data that one can refer to
 * within an SQML script.
 */
public class TableSource extends TableInput {

  @NonNull
  private final FlexibleDatasetSchema.TableField schema;
  @NonNull @Getter
  private final TableStatistic statistic;

  public TableSource(DataSystemConnector dataset, TableConfig configuration, NamePath path, Name name,
                     FlexibleDatasetSchema.TableField schema) {
    super(dataset, configuration, path, name);
    this.schema = schema;
    this.statistic = TableStatistic.of(1000); //TODO: extract from schema
  }

  public InputTableSchema getSchema() {
    return new InputTableSchema(schema, connector.hasSourceTimestamp());
  }
}