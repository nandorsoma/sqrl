package com.datasqrl.compile;

import com.datasqrl.config.CompilerConfiguration;
import com.datasqrl.config.EngineSettings;
import com.datasqrl.config.GlobalCompilerConfiguration;
import com.datasqrl.config.GlobalEngineConfiguration;
import com.datasqrl.config.error.ErrorCollector;
import com.datasqrl.graphql.generate.SchemaGenerator;
import com.datasqrl.graphql.inference.PgSchemaBuilder;
import com.datasqrl.graphql.inference.SchemaInference;
import com.datasqrl.graphql.inference.SchemaInferenceModel.InferredSchema;
import com.datasqrl.graphql.server.Model.RootGraphqlModel;
import com.datasqrl.graphql.util.ReplaceGraphqlQueries;
import com.datasqrl.parse.SqrlParser;
import com.datasqrl.physical.PhysicalPlan;
import com.datasqrl.physical.PhysicalPlanner;
import com.datasqrl.physical.database.QueryTemplate;
import com.datasqrl.plan.calcite.Planner;
import com.datasqrl.plan.calcite.PlannerFactory;
import com.datasqrl.plan.global.DAGPlanner;
import com.datasqrl.plan.global.OptimizedDAG;
import com.datasqrl.plan.local.generate.Resolve;
import com.datasqrl.plan.local.generate.Resolve.Env;
import com.datasqrl.plan.local.generate.Session;
import com.datasqrl.plan.queries.APIQuery;
import com.datasqrl.spi.ManifestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.idl.SchemaPrinter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.SqrlCalciteSchema;
import org.apache.calcite.sql.ScriptNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class Compiler {

  private final boolean write;

  public Compiler() {
    this(true);
  }

  public Compiler(boolean write) {
    this.write = write;
  }

  /**
   * Processes all the files in the build directory and creates the execution artifacts
   *
   * @return
   */
  @SneakyThrows
  public CompilerResult run(ErrorCollector collector, Path packageFile) {
    Preconditions.checkArgument(Files.isRegularFile(packageFile));
    SqrlCalciteSchema schema = new SqrlCalciteSchema(
        CalciteSchema.createRootSchema(false, false).plus());

    Path buildDir = packageFile.getParent();
    GlobalCompilerConfiguration globalConfig = GlobalEngineConfiguration.readFrom(packageFile, GlobalCompilerConfiguration.class);
    CompilerConfiguration config = globalConfig.initializeCompiler(collector);
    EngineSettings engineSettings = globalConfig.initializeEngines(collector);
    Planner planner = new PlannerFactory(schema.plus()).createPlanner();
    Session s = new Session(collector, planner, engineSettings.getPipeline());
    Resolve resolve = new Resolve(buildDir);

    ManifestConfiguration manifest = globalConfig.getManifest();
    Preconditions.checkArgument(manifest!=null);
    Path mainScript = buildDir.resolve(manifest.getMain());
    Optional<Path> graphqlSchema = manifest.getOptGraphQL().map(file -> buildDir.resolve(file));

    String str = Files.readString(mainScript);

    ScriptNode ast = SqrlParser.newParser()
        .parse(str);

    Env env = resolve.planDag(s, ast);

    String gqlSchema = inferOrGetSchema(env, graphqlSchema);

    InferredSchema inferredSchema = new SchemaInference(gqlSchema, env.getRelSchema(),
        env.getSession().getPlanner().getRelBuilder())
        .accept();

    PgSchemaBuilder pgSchemaBuilder = new PgSchemaBuilder(gqlSchema,
        env.getRelSchema(),
        env.getSession().getPlanner().getRelBuilder(),
        env.getSession().getPlanner());

    RootGraphqlModel root = inferredSchema.accept(pgSchemaBuilder, null);

    OptimizedDAG dag = optimizeDag(pgSchemaBuilder.getApiQueries(), env);
    PhysicalPlan plan = createPhysicalPlan(dag, env, s);

    root = updateGraphqlPlan(root, plan.getDatabaseQueries());

    if (write) {
      Path deploy = buildDir.getParent();
      writeGraphql(deploy, root, gqlSchema);
    }
    return new CompilerResult(root, plan);
  }

  @Value
  public class CompilerResult {
    RootGraphqlModel model;
    PhysicalPlan plan;
  }

  private OptimizedDAG optimizeDag(List<APIQuery> queries, Env env) {
    DAGPlanner dagPlanner = new DAGPlanner(env.getSession().getPlanner(),
        env.getSession().getPipeline());
    CalciteSchema relSchema = env.getRelSchema();
    return dagPlanner.plan(relSchema, queries, env.getExports());
  }

  private RootGraphqlModel updateGraphqlPlan(RootGraphqlModel root, Map<APIQuery, QueryTemplate> queries) {
    ReplaceGraphqlQueries replaceGraphqlQueries = new ReplaceGraphqlQueries(queries);
    root.accept(replaceGraphqlQueries, null);
    return root;
  }

  private PhysicalPlan createPhysicalPlan(OptimizedDAG dag, Env env, Session s) {
    PhysicalPlanner physicalPlanner = new PhysicalPlanner(s.getPlanner().getRelBuilder());
    PhysicalPlan physicalPlan = physicalPlanner.plan(dag);
    return physicalPlan;
  }

  @SneakyThrows
  public String inferOrGetSchema(Env env, Optional<Path> graphqlSchema) {
    if (graphqlSchema.map(s -> s.toFile().exists()).orElse(false)) {
      return Files.readString(graphqlSchema.get());
    }
    GraphQLSchema schema = SchemaGenerator.generate(env.getRelSchema());

    SchemaPrinter.Options opts = SchemaPrinter.Options.defaultOptions()
        .setComparators(GraphqlTypeComparatorRegistry.AS_IS_REGISTRY)
        .includeDirectives(false);
    String schemaStr = new SchemaPrinter(opts).print(schema);

    return schemaStr;
  }

  @SneakyThrows
  private void writeGraphql(Path file, RootGraphqlModel root, String gqlSchema) {
    try {
      file.resolve("schema.graphqls").toFile().delete();
      Files.writeString(file.resolve("schema.graphqls"),
          gqlSchema, StandardOpenOption.CREATE);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}