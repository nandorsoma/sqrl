package com.datasqrl.engine.database.relational;

import com.datasqrl.calcite.Dialect;
import com.datasqrl.calcite.SqrlFramework;
import com.datasqrl.calcite.convert.SnowflakeSqlNodeToString;
import com.datasqrl.calcite.dialect.snowflake.SqlCreateIcebergTableFromObjectStorage;
import com.datasqrl.calcite.dialect.snowflake.SqlCreateSnowflakeView;
import com.datasqrl.config.ConnectorFactoryFactory;
import com.datasqrl.config.EngineFactory;
import com.datasqrl.config.EngineFactory.Type;
import com.datasqrl.config.JdbcDialect;
import com.datasqrl.config.PackageJson;
import com.datasqrl.config.PackageJson.EmptyEngineConfig;
import com.datasqrl.config.PackageJson.EngineConfig;
import com.datasqrl.datatype.DataTypeMapper;
import com.datasqrl.datatype.snowflake.SnowflakeIcebergDataTypeMapper;
import com.datasqrl.engine.database.DatabasePhysicalPlan;
import com.datasqrl.engine.database.DatabaseViewPhysicalPlan;
import com.datasqrl.engine.database.DatabaseViewPhysicalPlan.DatabaseView;
import com.datasqrl.engine.database.DatabaseViewPhysicalPlan.DatabaseViewImpl;
import com.datasqrl.engine.database.QueryTemplate;
import com.datasqrl.engine.pipeline.ExecutionPipeline;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.plan.global.PhysicalDAGPlan.DatabaseStagePlan;
import com.datasqrl.plan.global.PhysicalDAGPlan.EngineSink;
import com.datasqrl.plan.global.PhysicalDAGPlan.ReadQuery;
import com.datasqrl.plan.global.PhysicalDAGPlan.StagePlan;
import com.datasqrl.plan.global.PhysicalDAGPlan.StageSink;
import com.datasqrl.plan.queries.IdentifiedQuery;
import com.datasqrl.sql.SqlDDLStatement;
import com.datasqrl.util.StreamUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.Value;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelShuttleImpl;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.parser.SqlParserPos;

public class SnowflakeEngine extends AbstractJDBCQueryEngine {

  private final SnowflakeSqlNodeToString sqlToString = new SnowflakeSqlNodeToString();


  @Inject
  public SnowflakeEngine(
      @NonNull PackageJson json,
      ConnectorFactoryFactory connectorFactory) {
    super(SnowflakeEngineFactory.ENGINE_NAME,
        json.getEngines().getEngineConfig(SnowflakeEngineFactory.ENGINE_NAME)
            .orElseGet(()-> new EmptyEngineConfig(SnowflakeEngineFactory.ENGINE_NAME)),
        connectorFactory);
  }

  @Override
  protected JdbcDialect getDialect() {
    return JdbcDialect.Snowflake;
  }

  @Override
  public DatabasePhysicalPlan plan(ConnectorFactoryFactory tableConnectorFactory, EngineConfig tableConnectorConfig,
      StagePlan plan, List<StageSink> inputs, ExecutionPipeline pipeline,
      List<StagePlan> stagePlans, SqrlFramework framework, ErrorCollector errorCollector) {

    //queries and views are generated by super
    JDBCPhysicalPlan jdbcPlan = (JDBCPhysicalPlan) super.plan(plan, inputs, pipeline, stagePlans, framework, errorCollector);

    //ddls require custom generation
    List<SqlDDLStatement> ddlStatements = new ArrayList<>();
    EngineConfig engineConfig = connectorConfig;
    for (EngineSink sink : StreamUtil.filterByClass(inputs, EngineSink.class).collect(Collectors.toList())) {
      SqlLiteral externalVolume = SqlLiteral.createCharString(
          (String)engineConfig.toMap().get("external-volume"), SqlParserPos.ZERO);

      SqlCreateIcebergTableFromObjectStorage icebergTable = new SqlCreateIcebergTableFromObjectStorage(SqlParserPos.ZERO,
          true, false,
          new SqlIdentifier(sink.getNameId(), SqlParserPos.ZERO),
          externalVolume,
          SqlLiteral.createCharString((String)engineConfig.toMap().get("catalog-name"),
              SqlParserPos.ZERO),
          SqlLiteral.createCharString(sink.getNameId(), SqlParserPos.ZERO),
          null,
          null, null, null);

      SnowflakeSqlNodeToString toString = new SnowflakeSqlNodeToString();
      String sql = toString.convert(() -> icebergTable).getSql();
      ddlStatements.add(()->sql);
    }

    return new JDBCPhysicalPlan(ddlStatements, jdbcPlan.getViews(), jdbcPlan.getQueryPlans());
  }

  protected String createView(SqlIdentifier viewNameIdentifier, SqlParserPos pos, SqlNodeList columnList, SqlNode viewSqlNode) {
    SqlCreateSnowflakeView createView = new SqlCreateSnowflakeView(pos, true, false, false, false, null, viewNameIdentifier, columnList,
        viewSqlNode, null, false);
    return sqlToString.convert(() -> createView).getSql() + ";";
  }


  protected Optional<DataTypeMapper> getUpCastingMapper() {
    return Optional.of(new SnowflakeIcebergDataTypeMapper());
  }


  @Override
  public @NonNull EngineFactory.Type getType() {
    return Type.QUERY;
  }
}
