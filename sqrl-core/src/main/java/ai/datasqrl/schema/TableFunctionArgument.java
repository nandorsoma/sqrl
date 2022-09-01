package ai.datasqrl.schema;

import lombok.Value;
import org.apache.calcite.sql.SqlDataTypeSpec;
import org.apache.calcite.sql.SqlIdentifier;

@Value
public class TableFunctionArgument {
  SqlIdentifier name;
  SqlDataTypeSpec type;
}
