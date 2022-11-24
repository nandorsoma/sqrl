package org.apache.calcite.sql;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.apache.calcite.sql.parser.SqlParserPos;

@Getter
public class QueryAssignment extends Assignment {

  private final Optional<List<TableFunctionArgument>> tableArgs;
  private final SqlNode query;

  public QueryAssignment(SqlParserPos location, SqlIdentifier namePath,
      Optional<List<TableFunctionArgument>> tableArgs, SqlNode query, Optional<SqlNodeList> hints) {
    super(location, namePath, hints);
    this.tableArgs = tableArgs;
    this.query = query;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    QueryAssignment that = (QueryAssignment) o;
    return Objects.equals(query, that.query) && Objects.equals(hints, that.hints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(query, hints);
  }

  @Override
  public void unparse(SqlWriter sqlWriter, int i, int i1) {
    super.unparse(sqlWriter, i, i1);
  }
}