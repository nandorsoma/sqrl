/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.engine.stream.flink.plan;

import com.datasqrl.engine.stream.StreamHolder;
import com.datasqrl.engine.stream.flink.DescriptorFactory;
import com.datasqrl.engine.stream.flink.FlinkStreamBuilder;
import com.datasqrl.engine.stream.flink.InputError;
import com.datasqrl.engine.stream.flink.InputError.InputErrorMessage;
import com.datasqrl.engine.stream.flink.InputError.Map2InputErrorMessage;
import com.datasqrl.engine.stream.flink.plan.FlinkTableRegistration.FlinkTableRegistrationContext;
import com.datasqrl.engine.stream.flink.plan.FlinkTableRegistration.SinkContext;
import com.datasqrl.engine.stream.flink.schema.UniversalTable2FlinkSchema;
import com.datasqrl.engine.stream.plan.TableRegistration;
import com.datasqrl.error.ErrorLocation;
import com.datasqrl.error.ErrorPrefix;
import com.datasqrl.io.SourceRecord;
import com.datasqrl.io.SourceRecord.Raw;
import com.datasqrl.io.tables.TableSource;
import com.datasqrl.io.util.StreamInputPreparer;
import com.datasqrl.io.util.StreamInputPreparerImpl;
import com.datasqrl.plan.calcite.table.ImportedRelationalTable;
import com.datasqrl.plan.calcite.table.SourceRelationalTable;
import com.datasqrl.plan.calcite.table.StreamRelationalTable;
import com.datasqrl.plan.calcite.table.StreamTableSchemaStream;
import com.datasqrl.plan.calcite.util.SqrlRexUtil;
import com.datasqrl.plan.global.OptimizedDAG.EngineSink;
import com.datasqrl.plan.global.OptimizedDAG.ExternalSink;
import com.datasqrl.plan.global.OptimizedDAG.QueryVisitor;
import com.datasqrl.plan.global.OptimizedDAG.ReadQuery;
import com.datasqrl.plan.global.OptimizedDAG.SinkVisitor;
import com.datasqrl.plan.global.OptimizedDAG.WriteQuery;
import com.datasqrl.schema.input.SchemaValidator;
import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.Set;
import lombok.Value;
import org.apache.calcite.rel.RelNode;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.bridge.java.StreamStatementSet;
import org.apache.flink.table.api.bridge.java.internal.StreamTableEnvironmentImpl;
import org.apache.flink.table.api.internal.FlinkEnvProxy;

public class FlinkTableRegistration implements
    TableRegistration<Void, FlinkTableRegistrationContext>,
    QueryVisitor<Void, FlinkTableRegistrationContext>,
    SinkVisitor<TableDescriptor, SinkContext> {

  private final Set<String> registeredTables = new HashSet<>();

  @Override
  public Void accept(StreamRelationalTable table, FlinkTableRegistrationContext context) {
    if (!addSource(table)) {
      return null;
    }

    RelNode relnode = table.getBaseRelation();
    /* Flink does not produce a correct changelog stream if there is a filter after the logical
       operation that produces the state (such as window deduplication), because the filter is applied
       to the changelog stream.
       The following checks whether this is the case
    */
    boolean unmodifiedChangelog = SqrlRexUtil.isDedupedRelNode(relnode, true, false);
    //physically rewrite the relnode for Flink and create table from it
    Table inputTable = makeTable(relnode, context);


    StreamTableSchemaStream schema2Stream = new StreamTableSchemaStream(table.getStreamSchema());
    Schema schema = new UniversalTable2FlinkSchema().convertSchema(table.getStreamSchema());
    DataStream stream = schema2Stream.convertToStream(context.getTEnv(),
      new StreamRelationalTableContext(inputTable, table.getStateChangeType(),
          unmodifiedChangelog, table.getBaseTableMetaData()));

    context.getTEnv().createTemporaryView(table.getNameId(), stream, schema);

    return null;
  }

  @Override
  public Void accept(ImportedRelationalTable table, FlinkTableRegistrationContext context) {
    if (!addSource(table)) {
      return null;
    }
    //TODO: if we are reading data in strict mode, we can use table API connectors directly which can be more efficient
    TableSource tableSource = table.getTableSource();
    StreamInputPreparer streamPreparer = new StreamInputPreparerImpl();
    ErrorLocation errorLocation = ErrorPrefix.INPUT_DATA.resolve(tableSource.getName());

    //TODO: push down startup timestamp if determined in FlinkPhysicalPlanner
    StreamHolder<Raw> stream = streamPreparer.getRawInput(tableSource, context.getBuilder(), errorLocation);
    SchemaValidator schemaValidator = tableSource.getSchemaValidator();
    StreamHolder<SourceRecord.Named> validate = stream.mapWithError(schemaValidator.getFunction(),
        errorLocation, SourceRecord.Named.class);
    context.getBuilder().addAsTable(validate, tableSource.getSchema(), table.getNameId());

    return null;
  }

  private boolean addSource(SourceRelationalTable table) {
    if (registeredTables.contains(table.getNameId())) {
      return false;
    }
    registeredTables.add(table.getNameId());
    return true;
  }


  public Table makeTable(RelNode relNode, FlinkTableRegistrationContext context) {
    FlinkPhysicalPlanRewriter rewriter = new FlinkPhysicalPlanRewriter(context.getTEnv(), this, context);
    RelNode rewritten = rewriter.rewrite(relNode);
    return FlinkEnvProxy.relNodeQuery(rewritten, context.getTEnv());
  }

  @Override
  public Void accept(ReadQuery query, FlinkTableRegistrationContext context) {
    throw new RuntimeException("Unexpected query type");
  }

  @Override
  public Void accept(WriteQuery query, FlinkTableRegistrationContext context) {
    String flinkSinkName = query.getSink().getName() + "_sink";
    Preconditions.checkArgument(!ArrayUtils.contains(context.getTEnv().listTables(), flinkSinkName),
        "Table already defined: %s", flinkSinkName);

    Table tbl = makeTable(query.getRelNode(), context);

    Schema tblSchema = tbl.getSchema().toSchema();
    TableDescriptor sinkDescriptor = query.getSink().accept(this, new SinkContext(tblSchema));

    context.getTEnv().createTemporaryTable(flinkSinkName, sinkDescriptor);

    context.getStreamStatementSet().addInsert(flinkSinkName, tbl);
    return null;
  }

  public static final String ERROR_SINK_NAME = "errors_internal_sink";

  public void registerErrors(DataStream<InputError> errorStream, ExternalSink errorSink,
      FlinkTableRegistrationContext context) {
    DataStream<InputErrorMessage> errorMessages = errorStream.flatMap(new Map2InputErrorMessage());
    Schema errorTableSchema = InputError.InputErrorMessage.getTableSchema();
    Table errorTable = context.getTEnv().fromDataStream(errorMessages, errorTableSchema);
    TableDescriptor sinkDescriptor = new DescriptorFactory().createSink(errorSink, errorTableSchema);
    context.getTEnv().createTemporaryTable(ERROR_SINK_NAME, sinkDescriptor);
    context.getStreamStatementSet().addInsert(ERROR_SINK_NAME, errorTable);
  }

  @Value
  class SinkContext {

    Schema tblSchema;
  }

  @Override
  public TableDescriptor accept(ExternalSink sink, SinkContext context) {
    DescriptorFactory descriptorFactory = new DescriptorFactory();
    return descriptorFactory.createSink(sink, context.getTblSchema());
  }

  @Override
  public TableDescriptor accept(EngineSink sink, SinkContext context) {
    DescriptorFactory descriptorFactory = new DescriptorFactory();
    return descriptorFactory.createSink(sink, context.getTblSchema());
  }

  @Value
  public static class FlinkTableRegistrationContext implements TableRegistrationContext {

    StreamTableEnvironmentImpl tEnv;
    FlinkStreamBuilder builder;
    StreamStatementSet streamStatementSet;
  }
}