package com.datasqrl.function;

import lombok.experimental.UtilityClass;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperatorBinding;
import org.apache.calcite.sql.SqlUnresolvedFunction;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlReturnTypeInference;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedAggFunction;
import org.apache.calcite.util.Optionality;
import org.apache.flink.calcite.shaded.org.checkerframework.checker.nullness.qual.Nullable;
import org.apache.flink.table.functions.FunctionDefinition;

@UtilityClass
public class CalciteFunctionUtil {

  /**
   * Operator that is used generally for rex planning where no operand inference or return type
   * inference happens.
   */
  public static SqlUnresolvedFunction lightweightOp(FunctionDefinition functionDefinition) {
    return lightweightOp(FlinkUdfNsObject.getFunctionName(functionDefinition));
  }
  public static SqlUnresolvedFunction lightweightOp(String name) {
    return new SqlUnresolvedFunction(new SqlIdentifier(name, SqlParserPos.ZERO),
        null, null, null,
        null, SqlFunctionCategory.USER_DEFINED_FUNCTION);
  }

  public static SqlUnresolvedFunction lightweightOp(String name, SqlReturnTypeInference returnType) {
    return new SqlUnresolvedFunction(new SqlIdentifier(name, SqlParserPos.ZERO),
        returnType, null, null,
        null, SqlFunctionCategory.USER_DEFINED_FUNCTION) {
      @Override
      public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
        return returnType.inferReturnType(opBinding);
      }
    };
  }

  public static SqlBinaryOperator lightweightBiOp(String name) {
    return new SqlBinaryOperator(name,
        SqlKind.OTHER_FUNCTION, 22, true, ReturnTypes.explicit(SqlTypeName.ANY),
        null, null);
  }

  public static SqlUserDefinedAggFunction lightweightAggOp(String name) {
    return new SqlUserDefinedAggFunction(new SqlIdentifier(name, SqlParserPos.ZERO),
        SqlKind.OTHER_FUNCTION,
        null, null, null,null, false, false, Optionality.IGNORED);
  }
}
