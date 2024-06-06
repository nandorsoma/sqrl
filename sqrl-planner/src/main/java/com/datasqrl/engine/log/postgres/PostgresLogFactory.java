package com.datasqrl.engine.log.postgres;

import com.datasqrl.canonicalizer.Name;
import com.datasqrl.config.ConnectorFactory;
import com.datasqrl.config.ConnectorFactory.IConnectorFactoryContext;
import com.datasqrl.config.ConnectorFactoryContext;
import com.datasqrl.config.TableConfig;
import com.datasqrl.engine.log.Log;
import com.datasqrl.engine.log.LogEngine.Timestamp;
import com.datasqrl.engine.log.LogFactory;
import com.datasqrl.io.tables.TableSchema;
import com.datasqrl.plan.table.RelDataTypeTableSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.calcite.rel.type.RelDataTypeField;

@AllArgsConstructor
public class PostgresLogFactory implements LogFactory {

  ConnectorFactory connectorFactory;

  @Override
  public Log create(String logId, RelDataTypeField schema, List<String> primaryKey,
      Timestamp timestamp) {

    String tableName = logId;
    Name logName = Name.system(schema.getName());
    IConnectorFactoryContext connectorContext = createSinkContext(logName, tableName, timestamp.getName(),
        timestamp.getType().name(), primaryKey);
    TableConfig logConfig = connectorFactory
        .createSourceAndSink(connectorContext);
    Optional<TableSchema> tblSchema = Optional.of(new RelDataTypeTableSchema(schema.getType()));
    return new PostgresTable(tableName, logName, logConfig, tblSchema, connectorContext);
  }

  private IConnectorFactoryContext createSinkContext(Name name, String tableName,
      String timestampName, String timestampType, List<String> primaryKey) {
    Map<String, Object> context = new HashMap<>();
    context.put("table-name", tableName);
    context.put("timestamp-name", timestampName);
    context.put("timestamp-type", timestampType);
    context.put("primary-key", primaryKey);
    return new ConnectorFactoryContext(name, context);
  }
}