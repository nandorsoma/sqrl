package ai.dataeng.sqml.metadata;

import ai.dataeng.sqml.env.SqmlEnv;
import ai.dataeng.sqml.execution.importer.ImportManagerFactory;
import ai.dataeng.sqml.function.FunctionProvider;
import lombok.Value;

@Value
public class Metadata {

  private final FunctionProvider functionProvider;
  private final SqmlEnv env;
  private final ImportManagerFactory importManagerFactory;

  public ImportManagerFactory getImportManagerFactory() {
    return importManagerFactory;
  }
}
