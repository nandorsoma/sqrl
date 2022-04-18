package ai.datasqrl.config.provider;

import ai.datasqrl.execute.StreamEngine;

public interface StreamMonitorProvider {

    StreamEngine.SourceMonitor create(StreamEngine engine, JDBCConnectionProvider jdbc, MetadataStoreProvider metaProvider,
                                      SerializerProvider serializerProvider,
                                      DatasetRegistryPersistenceProvider registryProvider);

}