package ai.datasqrl.sqrl2sql;

import ai.datasqrl.AbstractSQRLIT;
import ai.datasqrl.IntegrationTestSettings;
import ai.datasqrl.config.error.ErrorCollector;
import ai.datasqrl.config.scripts.ScriptBundle;
import ai.datasqrl.environment.ImportManager;
import ai.datasqrl.parse.ConfiguredSqrlParser;
import ai.datasqrl.parse.tree.Node;
import ai.datasqrl.parse.tree.ScriptNode;
import ai.datasqrl.parse.tree.SqrlStatement;
import ai.datasqrl.plan.calcite.Planner;
import ai.datasqrl.plan.calcite.PlannerFactory;
import ai.datasqrl.plan.calcite.SqrlTypeFactory;
import ai.datasqrl.plan.calcite.SqrlTypeSystem;
import ai.datasqrl.plan.calcite.sqrl.table.CalciteTableFactory;
import ai.datasqrl.plan.local.analyze.Analysis;
import ai.datasqrl.plan.local.analyze.Analyzer;
import ai.datasqrl.plan.local.analyze.VariableFactory;
import ai.datasqrl.plan.local.generate.Generator;
import ai.datasqrl.schema.input.SchemaAdjustmentSettings;
import ai.datasqrl.util.data.C360;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.schema.BridgedCalciteSchema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.JoinDeclarationContainerImpl;
import org.apache.calcite.sql.SqlNodeBuilderImpl;
import org.apache.calcite.sql.TableMapperImpl;
import org.apache.calcite.sql.UniqueAliasGeneratorImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class LogicalPlanRewritingTest extends AbstractSQRLIT {

  ConfiguredSqrlParser parser;

  ErrorCollector errorCollector;
  ImportManager importManager;
  Analyzer analyzer;
  Analysis analysis;
  private Planner planner;
  //  private ScriptNode script;
  private Generator generator;

  @BeforeEach
  public void setup() throws IOException {
    errorCollector = ErrorCollector.root();
    initialize(IntegrationTestSettings.getInMemory(false));
    C360 example = C360.INSTANCE;

    example.registerSource(env);

    importManager = sqrlSettings.getImportManagerProvider()
        .createImportManager(env.getDatasetRegistry());
    ScriptBundle bundle = example.buildBundle().setIncludeSchema(true).getBundle();
    Assertions.assertTrue(importManager.registerUserSchema(bundle.getMainScript().getSchema(),
        ErrorCollector.root()));
    parser = ConfiguredSqrlParser.newParser(errorCollector);
    CalciteTableFactory tableFactory = new CalciteTableFactory(new SqrlTypeFactory(new SqrlTypeSystem()));
    analyzer = new Analyzer(importManager, SchemaAdjustmentSettings.DEFAULT, tableFactory,
        errorCollector);

    SchemaPlus rootSchema = CalciteSchema.createRootSchema(false, false).plus();
    String schemaName = "test";
    BridgedCalciteSchema subSchema = new BridgedCalciteSchema();
    rootSchema.add(schemaName, subSchema); //also give the subschema access

    PlannerFactory plannerFactory = new PlannerFactory(rootSchema);
    Planner planner = plannerFactory.createPlanner();
    this.planner = planner;


    TableMapperImpl tableMapper = new TableMapperImpl(new HashMap<>());
    UniqueAliasGeneratorImpl uniqueAliasGenerator = new UniqueAliasGeneratorImpl(Set.of());
    JoinDeclarationContainerImpl joinDecs = new JoinDeclarationContainerImpl();
    SqlNodeBuilderImpl sqlNodeBuilder = new SqlNodeBuilderImpl();

    generator = new Generator(new CalciteTableFactory(new SqrlTypeFactory(new SqrlTypeSystem())),
        SchemaAdjustmentSettings.DEFAULT,
        planner,
        importManager,
        uniqueAliasGenerator,
        joinDecs,
        sqlNodeBuilder,
        tableMapper,
        errorCollector,
        new VariableFactory()
    );
  }


  @Test
  public void testSimpleQuery() {
    runScript(
            "IMPORT ecommerce-data.Orders;\n"
          + "IMPORT ecommerce-data.Product;\n"
          + "IMPORT ecommerce-data.Customer;\n"
          + "EntryCount := SELECT e.quantity * e.unit_price - e.discount as price FROM Orders.entries e;\n"
    );
  }

  @Test
  @Disabled
  public void testSimpleTemporalJoin() {
    runScript(
            "IMPORT ecommerce-data.Orders;\n"
          + "IMPORT ecommerce-data.Product;\n"
          + "Product := DISTINCT Product ON productid ORDER BY _ingest_time DESC;\n"
          + "EntryCategories := SELECT e.productid, e.quantity * e.unit_price - e.discount as price, p.name FROM Orders.entries e JOIN Product p ON e.productid = p.productid;\n"
    );
  }

  @Test
  @Disabled
  public void testNestingTemporalJoin() {
    //This currently fails because an ON condition is missing
    runScript(
            "IMPORT ecommerce-data.Orders;\n"
          + "IMPORT ecommerce-data.Product;\n"
          + "Product := DISTINCT Product ON productid ORDER BY _ingest_time DESC;\n"
          + "EntryCategories := SELECT o.id, o.time, e.productid, e.quantity, p.name FROM Orders o JOIN o.entries e JOIN Product p ON e.productid = p.productid;\n"
    );
  }

  public void runScript(String script) {
    ScriptNode node = parser.parse(script);

    for (Node n : node.getStatements()) {
      analyzer.analyze((SqrlStatement) n);
      generator.generate((SqrlStatement)n);
    }
  }
}