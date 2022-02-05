package ai.dataeng.sqml.execution.flink.ingest.stats;

import ai.dataeng.sqml.execution.flink.ingest.DatasetRegistration;
import ai.dataeng.sqml.execution.flink.ingest.source.SourceRecord;
import ai.dataeng.sqml.type.basic.ProcessMessage.ProcessBundle;
import lombok.ToString;

@ToString
public class SourceTableStatistics implements Accumulator<SourceRecord<String>, SourceTableStatistics, DatasetRegistration> {


    final RelationStats relation;

    public SourceTableStatistics() {
        this.relation = new RelationStats();
    }

    public ProcessBundle<StatsIngestError> validate(SourceRecord<String> sourceRecord, DatasetRegistration dataset) {
        ProcessBundle<StatsIngestError> errors = new ProcessBundle<>();
        RelationStats.validate(sourceRecord.getData(),DocumentPath.ROOT,errors, dataset.getCanonicalizer());
        return errors;
    }

    @Override
    public void add(SourceRecord<String> sourceRecord, DatasetRegistration dataset) {
        //TODO: Analyze timestamps on record
        relation.add(sourceRecord.getData(), dataset.getCanonicalizer());
    }

    @Override
    public void merge(SourceTableStatistics accumulator) {
        relation.merge(accumulator.relation);
    }

    public long getCount() {
        return relation.getCount();
    }
}