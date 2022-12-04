package com.datasqrl.engine.stream.flink;

import com.datasqrl.io.stats.TableStatisticsStoreProvider;
import com.datasqrl.io.formats.TextLineFormat;
import com.datasqrl.io.impl.file.DirectoryDataSystem;
import com.datasqrl.io.impl.file.FilePath;
import com.datasqrl.io.impl.kafka.KafkaDataSystem;
import com.datasqrl.io.DataSystemConnector;
import com.datasqrl.io.SourceRecord;
import com.datasqrl.io.tables.TableConfig;
import com.datasqrl.io.tables.TableInput;
import com.datasqrl.io.stats.SourceTableStatistics;
import com.datasqrl.io.util.TimeAnnotatedRecord;
import com.datasqrl.engine.stream.StreamHolder;
import com.datasqrl.engine.stream.flink.monitor.KeyedSourceRecordStatistics;
import com.datasqrl.engine.stream.flink.monitor.SaveTableStatistics;
import com.datasqrl.engine.stream.flink.schema.FlinkRowConstructor;
import com.datasqrl.engine.stream.flink.schema.FlinkTableSchemaGenerator;
import com.datasqrl.engine.stream.flink.schema.FlinkTypeInfoSchemaGenerator;
import com.datasqrl.engine.stream.flink.util.FlinkUtilities;
import com.datasqrl.schema.UniversalTableBuilder;
import com.datasqrl.schema.converters.SourceRecord2RowMapper;
import com.datasqrl.schema.input.FlexibleTable2UTBConverter;
import com.datasqrl.schema.input.FlexibleTableConverter;
import com.datasqrl.schema.input.InputTableSchema;
import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.file.src.enumerate.NonSplittingRecursiveEnumerator;
import org.apache.flink.connector.kafka.source.KafkaSourceBuilder;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Getter
//TODO: Do output tags (errors, monitor) need a globally unique name or just local to the job?
public class FlinkStreamBuilder implements FlinkStreamEngine.Builder {

  public static final int DEFAULT_PARALLELISM = 16;
  public static final String STATS_NAME_PREFIX = "Stats";

  private final FlinkStreamEngine engine;
  private final StreamExecutionEnvironment environment;
  private final StreamTableEnvironment tableEnvironment;
  private final UUID uuid;
  private final int defaultParallelism = DEFAULT_PARALLELISM;
  private FlinkStreamEngine.JobType jobType;

  public static final String ERROR_TAG_PREFIX = "error";
  private Map<String, OutputTag<ProcessError>> errorTags = new HashMap<>();


  public FlinkStreamBuilder(FlinkStreamEngine engine, StreamExecutionEnvironment environment) {
    this.engine = engine;
    this.environment = environment;
    this.tableEnvironment = StreamTableEnvironment.create(environment);
//    FunctionCatalog catalog = FlinkEnvProxy.getFunctionCatalog((StreamTableEnvironmentImpl) tableEnvironment);

    this.uuid = UUID.randomUUID();
  }

  @Override
  public void setJobType(@NonNull FlinkStreamEngine.JobType jobType) {
    this.jobType = jobType;
  }

  @Override
  public FlinkStreamEngine.FlinkJob build() {
    return engine.createStreamJob(environment, jobType);
  }

  @Override
  public OutputTag<ProcessError> getErrorTag(final String errorName) {
    OutputTag<ProcessError> errorTag = errorTags.get(errorName);
    if (errorTag==null) {
      errorTag = new OutputTag<>(
              FlinkStreamEngine.getFlinkName(ERROR_TAG_PREFIX, errorName)) {
      };
      errorTags.put(errorName, errorTag);
    }
    return errorTag;
  }

  @Override
  public StreamHolder<SourceRecord.Raw> monitor(StreamHolder<SourceRecord.Raw> stream,
                                                TableInput tableSource,
                                                TableStatisticsStoreProvider.Encapsulated statisticsStoreProvider) {
    Preconditions.checkArgument(stream instanceof FlinkStreamHolder && ((FlinkStreamHolder)stream).getBuilder().equals(this));
    setJobType(FlinkStreamEngine.JobType.MONITOR);

    DataStream<SourceRecord.Raw> data = ((FlinkStreamHolder)stream).getStream();
    final OutputTag<SourceTableStatistics> statsOutput = new OutputTag<>(
            FlinkStreamEngine.getFlinkName(STATS_NAME_PREFIX, tableSource.qualifiedName())) {
    };

    SingleOutputStreamOperator<SourceRecord.Raw> process = data.keyBy(
                    FlinkUtilities.getHashPartitioner(defaultParallelism))
            .process(
                    new KeyedSourceRecordStatistics(statsOutput, tableSource.getDigest()), TypeInformation.of(SourceRecord.Raw.class));
//    process.addSink(new PrintSinkFunction<>()); //TODO: persist last 100 for querying

    //Process the gathered statistics in the side output
    final int randomKey = FlinkUtilities.generateBalancedKey(defaultParallelism);
    process.getSideOutput(statsOutput)
            .keyBy(FlinkUtilities.getSingleKeySelector(randomKey))
            .reduce(
                    new ReduceFunction<SourceTableStatistics>() {
                      @Override
                      public SourceTableStatistics reduce(SourceTableStatistics acc,
                                                          SourceTableStatistics add) throws Exception {
                        acc.merge(add);
                        return acc;
                      }
                    })
            .keyBy(FlinkUtilities.getSingleKeySelector(randomKey))
//                .process(new BufferedLatestSelector(getFlinkName(STATS_NAME_PREFIX, sourceTable),
//                        500, SourceTableStatistics.Accumulator.class), TypeInformation.of(SourceTableStatistics.Accumulator.class))
            .addSink(new SaveTableStatistics(statisticsStoreProvider, tableSource.getDigest()));
    return new FlinkStreamHolder<>(this,process);
  }

  @Override
  public void addAsTable(StreamHolder<SourceRecord.Named> stream, InputTableSchema schema, String qualifiedTableName) {
    Preconditions.checkArgument(stream instanceof FlinkStreamHolder && ((FlinkStreamHolder)stream).getBuilder().equals(this));
    FlinkStreamHolder<SourceRecord.Named> flinkStream = (FlinkStreamHolder)stream;
    UniversalTableBuilder tblBuilder = getUniversalTableBuilder(schema);

    TypeInformation typeInformation = FlinkTypeInfoSchemaGenerator.INSTANCE.convertSchema(tblBuilder);
    SourceRecord2RowMapper<Row> mapper = new SourceRecord2RowMapper(schema, FlinkRowConstructor.INSTANCE);

    //TODO: error handling when mapping doesn't work?
    SingleOutputStreamOperator<Row> rows = flinkStream.getStream().map(r -> mapper.apply(r),typeInformation);
    Schema tableSchema = FlinkTableSchemaGenerator.INSTANCE.convertSchema(tblBuilder);
    tableEnvironment.createTemporaryView(qualifiedTableName, rows, tableSchema);
  }

  public UniversalTableBuilder getUniversalTableBuilder(InputTableSchema schema) {
    FlexibleTableConverter converter = new FlexibleTableConverter(schema);
    FlexibleTable2UTBConverter utbConverter = new FlexibleTable2UTBConverter();
    return converter.apply(utbConverter);
  }

  @Override
  public StreamHolder<TimeAnnotatedRecord<String>> fromTextSource(TableInput table) {
    Preconditions.checkArgument(table.getParser() instanceof TextLineFormat.Parser, "This method only supports text sources");
    DataSystemConnector sourceConnector = table.getConnector();
    String flinkSourceName = table.getDigest().toString('-',"input");

    StreamExecutionEnvironment env = getEnvironment();
    DataStream<TimeAnnotatedRecord<String>> timedSource;
    if (sourceConnector instanceof DirectoryDataSystem.Connector) {
      DirectoryDataSystem.Connector filesource = (DirectoryDataSystem.Connector) sourceConnector;

      Duration monitorDuration = null;
//            if (filesource.getConfiguration().isDiscoverFiles()) monitorDuration = Duration.ofSeconds(10);
      FileEnumeratorProvider fileEnumerator = new FileEnumeratorProvider(filesource, table.getConfiguration());

      org.apache.flink.connector.file.src.FileSource.FileSourceBuilder<String> builder =
              org.apache.flink.connector.file.src.FileSource.forRecordStreamFormat(
                      new org.apache.flink.connector.file.src.reader.TextLineInputFormat(
                              table.getConfiguration().getCharset()), FilePath.toFlinkPath(filesource.getPath()));

      builder.setFileEnumerator(fileEnumerator);
      if (monitorDuration != null) {
        builder.monitorContinuously(Duration.ofSeconds(10));
      }

      //TODO: set watermarks
//              stream.assignTimestampsAndWatermarks(WatermarkStrategy.<SourceRecord>forMonotonousTimestamps().withTimestampAssigner((event, timestamp) -> event.getSourceTime().toEpochSecond()));

      DataStreamSource<String> textSource = env.fromSource(builder.build(),
              WatermarkStrategy.noWatermarks(), flinkSourceName);
      timedSource = textSource.map(new ToTimedRecord());

    } else if (sourceConnector instanceof KafkaDataSystem.Connector) {
      KafkaDataSystem.Connector kafkaSource = (KafkaDataSystem.Connector) sourceConnector;
      String topic = kafkaSource.getTopicPrefix() + table.getConfiguration().getIdentifier();
      String groupId = flinkSourceName + "-" + getUuid();

      KafkaSourceBuilder<TimeAnnotatedRecord<String>> builder = org.apache.flink.connector.kafka.source.KafkaSource.<TimeAnnotatedRecord<String>>builder()
              .setBootstrapServers(kafkaSource.getServersAsString())
              .setTopics(topic)
              .setStartingOffsets(OffsetsInitializer.earliest()) //TODO: work with commits
              .setGroupId(groupId);

      builder.setDeserializer(
              new KafkaTimeValueDeserializationSchemaWrapper<>(new SimpleStringSchema()));

      timedSource = env.fromSource(builder.build(),
              WatermarkStrategy.noWatermarks(), flinkSourceName);

    } else {
      throw new UnsupportedOperationException("Unrecognized source table type: " + table);
    }
    return new FlinkStreamHolder<>(this,timedSource);
  }

  private static class ToTimedRecord implements MapFunction<String,TimeAnnotatedRecord<String>> {

    @Override
    public TimeAnnotatedRecord<String> map(String s) throws Exception {
      return  new TimeAnnotatedRecord<>(s, null);
    }
  }

  @NoArgsConstructor
  @AllArgsConstructor
  private static class FileEnumeratorProvider implements
          org.apache.flink.connector.file.src.enumerate.FileEnumerator.Provider {

    DirectoryDataSystem.Connector directorySource;
    TableConfig table;

    @Override
    public org.apache.flink.connector.file.src.enumerate.FileEnumerator create() {
      return new NonSplittingRecursiveEnumerator(new FileNameMatcher());
    }

    private class FileNameMatcher implements Predicate<Path> {

      @Override
      public boolean test(Path path) {
        try {
          if (path.getFileSystem().getFileStatus(path).isDir()) {
            return true;
          }
        } catch (IOException e) {
          return false;
        }
        return directorySource.isTableFile(FilePath.fromFlinkPath(path), table);
      }
    }
  }


  static class KafkaTimeValueDeserializationSchemaWrapper<T> implements
          KafkaRecordDeserializationSchema<TimeAnnotatedRecord<T>> {

    private static final long serialVersionUID = 1L;
    private final DeserializationSchema<T> deserializationSchema;

    KafkaTimeValueDeserializationSchemaWrapper(DeserializationSchema<T> deserializationSchema) {
      this.deserializationSchema = deserializationSchema;
    }

    @Override
    public void open(DeserializationSchema.InitializationContext context) throws Exception {
      deserializationSchema.open(context);
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> message,
                            Collector<TimeAnnotatedRecord<T>> out)
            throws IOException {
      T result = deserializationSchema.deserialize(message.value());
      if (result != null) {
        out.collect(new TimeAnnotatedRecord<>(result, Instant.ofEpochSecond(message.timestamp())));
      }
    }

    @Override
    public TypeInformation<TimeAnnotatedRecord<T>> getProducedType() {
      return (TypeInformation) TypeInformation.of(TimeAnnotatedRecord.class);
      //return deserializationSchema.getProducedType();
    }
  }

}