/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.engine;

import com.datasqrl.calcite.SqrlFramework;
import com.datasqrl.config.EngineFactory.Type;
import com.datasqrl.config.TableConfig;
import com.datasqrl.engine.pipeline.ExecutionPipeline;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.plan.global.PhysicalDAGPlan.StagePlan;
import com.datasqrl.plan.global.PhysicalDAGPlan.StageSink;
import java.util.EnumSet;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * Describes a physical execution engine and it's capabilities.
 */
public interface ExecutionEngine extends IExecutionEngine {

  /**
   *
   * @param capability
   * @return whether the engine supports the given {@link EngineFeature}
   */
  boolean supports(EngineFeature capability);

  /**
   *
   * @param function
   * @return whether the engine can execute the given function
   */
//  boolean supports(FunctionDefinition function);

  /**
   * Returns the {@link TableConfig} for this engine so it can
   * be used as a sink by a previous stage in the pipeline.
   * @return
   */
  TableConfig getSinkConfig(String sinkName);

  /**
   * Create the physical plan from the {@link StagePlan} produced by the {@link com.datasqrl.plan.global.DAGPlanner}
   * for this engine.
   */
  EnginePhysicalPlan plan(StagePlan plan, List<StageSink> inputs,
      ExecutionPipeline pipeline, List<StagePlan> stagePlans, SqrlFramework framework, ErrorCollector errorCollector);

  @AllArgsConstructor
  @Getter
  abstract class Base implements ExecutionEngine {

    protected final @NonNull String name;
    protected final @NonNull Type type;
    protected final @NonNull EnumSet<EngineFeature> capabilities;

    @Override
    public boolean supports(EngineFeature capability) {
      return capabilities.contains(capability);
    }

    @Override
    public TableConfig getSinkConfig(String sinkName) {
      throw new UnsupportedOperationException("Not a sink");
    }
  }


}
