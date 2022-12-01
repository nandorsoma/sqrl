package ai.datasqrl.io.sources.stats;

import ai.datasqrl.metadata.MetadataStore;
import ai.datasqrl.metadata.MetadataStoreProvider;
import lombok.AllArgsConstructor;

import java.io.Serializable;

public interface TableStatisticsStoreProvider extends Serializable {

    TableStatisticsStore openStore(MetadataStore metaStore);

    interface Encapsulated extends Serializable {

        TableStatisticsStore openStore();

    }

    @AllArgsConstructor
    class EncapsulatedImpl implements Encapsulated {

        private final MetadataStoreProvider metaProvider;
        private final TableStatisticsStoreProvider statsProvider;

        @Override
        public TableStatisticsStore openStore() {
            MetadataStore store = metaProvider.openStore();
            return statsProvider.openStore(store);
        }
    }

}