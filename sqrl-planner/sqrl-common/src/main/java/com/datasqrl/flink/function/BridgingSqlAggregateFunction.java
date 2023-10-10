/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//Copied from flink as we incrementally phase out flink code for sqrl code
package com.datasqrl.flink.function;

import com.datasqrl.calcite.Dialect;
import com.datasqrl.calcite.function.RuleTransform;
import com.datasqrl.function.FunctionTranslationMap;
import com.datasqrl.util.ReflectionUtil;
import java.lang.reflect.Method;
import java.util.List;
import lombok.Getter;
import org.apache.calcite.adapter.enumerable.AggImplementor;
import org.apache.calcite.adapter.enumerable.CallImplementor;
import org.apache.calcite.adapter.enumerable.NullPolicy;
import org.apache.calcite.adapter.enumerable.ReflectiveCallNotNullImplementor;
import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.adapter.enumerable.RexImpTable.UserDefinedAggReflectiveImplementor;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.AggregateFunction;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.FunctionParameter;
import org.apache.calcite.schema.ImplementableAggFunction;
import org.apache.calcite.schema.ImplementableFunction;
import org.apache.calcite.schema.impl.AggregateFunctionImpl;
import org.apache.calcite.schema.impl.ReflectiveFunctionBase;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlOperandMetadata;
import org.apache.calcite.sql.type.SqlOperandTypeChecker;
import org.apache.calcite.sql.type.SqlOperandTypeInference;
import org.apache.calcite.sql.type.SqlReturnTypeInference;
import org.apache.calcite.sql.validate.SqlUserDefinedAggFunction;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.apache.calcite.util.Optionality;
import org.apache.calcite.util.ReflectUtil;
import org.apache.calcite.util.Static;
import org.apache.flink.calcite.shaded.com.google.common.collect.ImmutableList;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.functions.FunctionDefinition;
import org.apache.flink.table.planner.calcite.FlinkTypeFactory;
import org.apache.flink.table.planner.calcite.RexFactory;
import org.apache.flink.table.types.inference.TypeInference;

/**
 * Bridges a Flink function to calcite
 */
public class BridgingSqlAggregateFunction extends SqlUserDefinedAggFunction implements BridgingFunction, RuleTransform {
  private final String flinkName;
  private final DataTypeFactory dataTypeFactory;
  private final FlinkTypeFactory flinkTypeFactory;
  private final RexFactory rexFactory;
  @Getter
  private final org.apache.flink.table.functions.AggregateFunction definition;
  private final TypeInference typeInference;

  public BridgingSqlAggregateFunction(String name, String flinkName, DataTypeFactory dataTypeFactory,
                                   FlinkTypeFactory flinkTypeFactory, RexFactory rexFactory, SqlKind kind,
                                   org.apache.flink.table.functions.AggregateFunction definition, TypeInference typeInference) {
    super(
        new SqlIdentifier(name, SqlParserPos.ZERO),
        kind,
        createSqlReturnTypeInference(name, flinkTypeFactory, dataTypeFactory, definition),
        createSqlOperandTypeInference(name, flinkTypeFactory, dataTypeFactory, definition),
        createOperandMetadata(name, flinkTypeFactory, dataTypeFactory, definition),
        createCallableFlinkFunction(flinkTypeFactory, dataTypeFactory, definition),
        false,
        false,
        Optionality.IGNORED);
    this.flinkName = flinkName;

    this.dataTypeFactory = dataTypeFactory;
    this.flinkTypeFactory = flinkTypeFactory;
    this.rexFactory = rexFactory;
    this.definition = definition;
    this.typeInference = typeInference;
  }

  private static SqlOperandMetadata createOperandMetadata(String name, FlinkTypeFactory flinkTypeFactory, DataTypeFactory dataTypeFactory, FunctionDefinition definition) {
    return new FlinkOperandMetadata(flinkTypeFactory, dataTypeFactory, definition, definition.getTypeInference(dataTypeFactory));
  }

  public static SqlReturnTypeInference createSqlReturnTypeInference(String name, FlinkTypeFactory flinkTypeFactory, DataTypeFactory dataTypeFactory, FunctionDefinition definition) {
    return new FlinkSqlReturnTypeInference(flinkTypeFactory, dataTypeFactory, definition, definition.getTypeInference(dataTypeFactory));
  }

  public static SqlOperandTypeInference createSqlOperandTypeInference(String name, FlinkTypeFactory flinkTypeFactory, DataTypeFactory dataTypeFactory, FunctionDefinition definition) {
    return new FlinkSqlOperandTypeInference(flinkTypeFactory, dataTypeFactory, definition, definition.getTypeInference(dataTypeFactory));
  }

  @Override
  public List<String> getParamNames() {
    if (typeInference.getNamedArguments().isPresent()) {
      return typeInference.getNamedArguments().get();
    }
    return super.getParamNames();
  }

  @Override
  public boolean isDeterministic() {
    return definition.isDeterministic();
  }


  private static AggregateFunction createCallableFlinkFunction(FlinkTypeFactory flinkTypeFactory, DataTypeFactory dataTypeFactory, org.apache.flink.table.functions.AggregateFunction definition) {
//    Class clazz = definition.getClass();
//    Method initMethod = ReflectionUtil.findMethod(clazz, "createAccumulator");
//    Method addMethod = ReflectionUtil.findMethod(clazz, "accumulate");
//    Method mergeMethod = ReflectionUtil.findMethod(clazz, "merge");;
//    Method resultMethod = ReflectionUtil.findMethod(clazz, "result");
//    Class<?> accumulatorType = initMethod.getReturnType();
//    Class<?> resultType = resultMethod != null ? resultMethod.getReturnType() : accumulatorType;
//    List<Class> addParamTypes = ImmutableList.copyOf((Class[])addMethod.getParameterTypes());
//    if (!addParamTypes.isEmpty() && addParamTypes.get(0) == accumulatorType) {
//      ReflectiveFunctionBase.ParameterListBuilder params = ReflectiveFunctionBase.builder();
//      ImmutableList.Builder<Class<?>> valueTypes = ImmutableList.builder();
//
//      for (int i = 1; i < addParamTypes.size(); ++i) {
//        Class type = (Class) addParamTypes.get(i);
//        String name = ReflectUtil.getParameterName(addMethod, i);
//        boolean optional = ReflectUtil.isParameterOptional(addMethod, i);
//        params.add(type, name, optional);
//        valueTypes.add(type);
//      }
//    }
//
//    return new ImplementableAggFunction() {
//      @Override
//      public RelDataType getReturnType(RelDataTypeFactory relDataTypeFactory) {
//        throw new RuntimeException("todo");
//      }
//
//      @Override
//      public AggImplementor getImplementor(boolean b) {
//        return new RexImpTable.UserDefinedAggReflectiveImplementor(this);
//      }
//
//      @Override
//      public List<FunctionParameter> getParameters() {
//        //derive parameters (necessary?)
//        return List.of();
//      }
//    };
    return null;
  }

  @Override
  public List<RelRule> transform(Dialect dialect, SqlOperator operator) {
    if (definition instanceof RuleTransform) {
      return ((RuleTransform) definition).transform(dialect, this);
    }

    return List.of();
  }
}