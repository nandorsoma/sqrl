package ai.dataeng.sqml.config.provider;

import ai.dataeng.sqml.execution.flink.ingest.DatasetLookup;
import ai.dataeng.sqml.planner.operator.ImportResolver;

public interface ImportManagerProvider {
  ImportResolver createImportManager(DatasetLookup datasetLookup);
}