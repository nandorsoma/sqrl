/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.config;

import com.datasqrl.engine.EngineConfiguration;
import com.datasqrl.engine.ExecutionEngine;
import com.datasqrl.engine.database.DatabaseEngine;
import com.datasqrl.engine.database.DatabaseEngineConfiguration;
import com.datasqrl.engine.pipeline.SimplePipeline;
import com.datasqrl.engine.pipeline.ExecutionPipeline;
import com.datasqrl.engine.stream.StreamEngine;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.metadata.MetadataConfiguration;
import com.datasqrl.metadata.MetadataStoreProvider;
import com.datasqrl.util.ConfigurationUtil;
import com.datasqrl.util.StreamUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.net.URI;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.tuple.Pair;

import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for the engines
 * <p>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
public class GlobalEngineConfiguration implements GlobalConfiguration {

  public static final String ENGINES_PROPERTY = "engines";

  @Valid
  @JsonProperty(ENGINES_PROPERTY)
  List<EngineConfiguration> engines;

  @Builder.Default
  @NonNull @Valid
  MetadataConfiguration metadata = new MetadataConfiguration();

  public EngineSettings initializeEngines(ErrorCollector errors) {
    if (!ConfigurationUtil.javaxValidate(this, errors)) {
      return null;
    }

    Optional<DatabaseEngineConfiguration> dbOpt = StreamUtil.filterByClass(engines,
            DatabaseEngineConfiguration.class)
        .findFirst();
    if (dbOpt.isEmpty()) {
      errors.fatal("Need to configure a database engine");
      return null;
    }
    Optional<EngineConfiguration> streamOpt = engines.stream()
        .filter(conf -> conf.getEngineType() == ExecutionEngine.Type.STREAM)
        .findFirst();
    if (streamOpt.isEmpty()) {
      errors.fatal("Need to configure a stream engine");
      return null;
    }

    DatabaseEngine db = dbOpt.get().initialize(errors.resolve(dbOpt.get().getEngineName()));
    StreamEngine stream = (StreamEngine) streamOpt.get()
        .initialize(errors.resolve(streamOpt.get().getEngineName()));

    Optional<DatabaseEngineConfiguration> metaOpt = dbOpt;
    if (!Strings.isNullOrEmpty(metadata.getEngineType())) {
      metaOpt = StreamUtil.filterByClass(engines, DatabaseEngineConfiguration.class)
          .filter(eng -> eng.getEngineName().equalsIgnoreCase(metadata.getEngineType()))
          .findAny();
      if (metaOpt.isEmpty()) {
        errors.fatal("Could not find database engine for metadata store with name: %s",
            metadata.getEngineType());
        return null;
      }
    }
    MetadataStoreProvider metadataStoreProvider = metaOpt.get().getMetadataStore();
    ExecutionPipeline pipeline = SimplePipeline.of(stream, db);
    return new EngineSettings(pipeline, metadataStoreProvider, stream);
  }

  public void setDefaultEngines(DatabaseEngineConfiguration db, EngineConfiguration stream) {
    if (engines == null || engines.isEmpty()) {
      engines = new ArrayList<>();
      engines.add(stream);
      engines.add(db);
    }
  }
  public static <C extends GlobalEngineConfiguration> C readFrom(@NonNull URI uri,
      @NonNull Class<C> clazz) throws IOException {
    return readFrom(List.of(uri), clazz);
  }

  public static <C extends GlobalEngineConfiguration> C readFromPath(@NonNull List<Path> paths,
      @NonNull Class<C> clazz) throws IOException {
    return combinePackages(paths.stream()
        .map(p->p.toUri())
        .collect(Collectors.toList()), clazz).getKey();
  }

  public static <C extends GlobalEngineConfiguration> C readFrom(@NonNull List<URI> paths,
      @NonNull Class<C> clazz) throws IOException {
    return combinePackages(paths, clazz).getKey();
  }

  public static <C extends GlobalEngineConfiguration> Pair<C, JsonNode> combinePackages(
      @NonNull List<URI> paths, @NonNull Class<C> clazz) throws IOException {
    Preconditions.checkArgument(!paths.isEmpty());
    SimpleModule module = new SimpleModule();
    module.addDeserializer(EngineConfiguration.class, new EngineConfiguration.Deserializer());
    ObjectMapper mapper = new ObjectMapper().registerModule(module);
    JsonNode combinedPackage = mapper.readValue(paths.get(0).toURL(), JsonNode.class);
    for (int i = 1; i < paths.size(); i++) {
      combinedPackage = mapper.readerForUpdating(combinedPackage).readValue(paths.get(i).toURL());
    }
    return Pair.of(mapper.convertValue(combinedPackage, clazz), combinedPackage);
  }


}