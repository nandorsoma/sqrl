package com.datasqrl.datatype.flink.jdbc;

import com.datasqrl.config.TableConfig;
import com.datasqrl.datatype.DataTypeMapper;
import com.datasqrl.datatype.SerializeToBytes;
import com.datasqrl.datatype.flink.FlinkDataTypeMapper;
import com.datasqrl.engine.stream.flink.connector.CastFunction;
import com.datasqrl.json.FlinkJsonType;
import com.datasqrl.json.JsonToString;
import com.datasqrl.vector.FlinkVectorType;
import com.datasqrl.vector.VectorToDouble;
import com.google.auto.service.AutoService;
import java.util.Optional;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.flink.table.planner.plan.schema.RawRelDataType;

@AutoService(DataTypeMapper.class)
public class FlinkPostgresDataTypeMapper extends FlinkDataTypeMapper {

  public boolean nativeTypeSupport(RelDataType type) {
    switch (type.getSqlTypeName()) {
      case TINYINT:
      case REAL:
      case INTERVAL_YEAR:
      case INTERVAL_YEAR_MONTH:
      case INTERVAL_MONTH:
      case INTERVAL_DAY:
      case INTERVAL_DAY_HOUR:
      case INTERVAL_DAY_MINUTE:
      case INTERVAL_DAY_SECOND:
      case INTERVAL_HOUR:
      case INTERVAL_HOUR_MINUTE:
      case INTERVAL_HOUR_SECOND:
      case INTERVAL_MINUTE:
      case INTERVAL_MINUTE_SECOND:
      case INTERVAL_SECOND:
      case NULL:
      case SYMBOL:
      case MULTISET:
      case DISTINCT:
      case STRUCTURED:
      case OTHER:
      case CURSOR:
      case COLUMN_LIST:
      case DYNAMIC_STAR:
      case GEOMETRY:
      case SARG:
      case ANY:
      default:
        return false;
      case BOOLEAN:
      case SMALLINT:
      case INTEGER:
      case BIGINT:
      case DECIMAL:
      case FLOAT:
      case DOUBLE:
      case DATE:
      case TIME:
      case TIME_WITH_LOCAL_TIME_ZONE:
      case TIMESTAMP:
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
      case CHAR:
      case VARCHAR:
      case BINARY:
      case VARBINARY:
        return true;
      case ARRAY:
        return false;
      case MAP:
        return false;
      case ROW:
        return false;
    }
  }

  @Override
  public Optional<CastFunction> convertType(RelDataType type) {
    if (nativeTypeSupport(type)) {
      return Optional.empty(); //no cast needed
    }

    // Explicit downcast for json
    if (type instanceof RawRelDataType) {
      RawRelDataType rawRelDataType = (RawRelDataType) type;
      if (rawRelDataType.getRawType().getDefaultConversion() == FlinkJsonType.class) {
        return Optional.of(
            new CastFunction(JsonToString.class.getName(),
                convert(new JsonToString())));
      } else if (rawRelDataType.getRawType().getDefaultConversion() == FlinkVectorType.class) {
        return Optional.of(
            new CastFunction(VectorToDouble.class.getName(),
                convert(new VectorToDouble())));
      }
    }

    // Cast needed, convert to bytes
    return Optional.of(
        new CastFunction(SerializeToBytes.class.getSimpleName(),
            convert(new SerializeToBytes())));
  }

  @Override
  public boolean isTypeOf(TableConfig tableConfig) {
    Optional<String> connectorNameOpt = tableConfig.getConnectorConfig().getConnectorName();
    if (connectorNameOpt.isEmpty()) {
      return false;
    }

    String connectorName = connectorNameOpt.get();
    if (!connectorName.equalsIgnoreCase("jdbc")) {
      return false;
    }

    String url = (String)tableConfig.getConnectorConfig().toMap().get("url");
    return url.toLowerCase().startsWith("jdbc:postgresql:");
  }
}
