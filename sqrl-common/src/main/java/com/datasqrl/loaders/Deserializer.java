/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.loaders;

import com.datasqrl.spi.JacksonDeserializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

public class Deserializer {

  final ObjectMapper jsonMapper;
  final YAMLMapper yamlMapper;

  public Deserializer() {
    SimpleModule module = new SimpleModule();
    ServiceLoader<JacksonDeserializer> moduleLoader = ServiceLoader.load(JacksonDeserializer.class);
    for (JacksonDeserializer deserializer : moduleLoader) {
      module.addDeserializer(deserializer.getSuperType(), deserializer);
    }

    jsonMapper = new ObjectMapper().registerModule(module)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    yamlMapper = new YAMLMapper();
  }

  public <T> T mapJsonFile(Path path, Class<T> clazz) {
    return mapFile(jsonMapper, path, clazz);
  }

  public <T> T mapYAMLFile(Path path, Class<T> clazz) {
    return mapFile(yamlMapper, path, clazz);
  }

  public static <T> T mapFile(ObjectMapper mapper, Path path, Class<T> clazz) {
    try {
      return mapper.readValue(path.toFile(), clazz);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public <O> O mapJsonField(Path file, String field, Class<O> clazz) throws IOException {
    JsonNode jsonNode = jsonMapper.readValue(file.toFile(), JsonNode.class);
    return jsonMapper.treeToValue(jsonNode.get(field),clazz);
  }

  public boolean hasJsonField(Path file, String field) throws IOException {
    JsonNode jsonNode = jsonMapper.readValue(file.toFile(), JsonNode.class);
    return jsonNode.has(field) && jsonNode.hasNonNull(field);
  }

  public <O> void writeToJson(Path file, O object) throws IOException {
    writeToJson(file, object, false);
  }

  public <O> void writeToJson(Path file, O object, boolean pretty) throws IOException {
    ObjectWriter writer = jsonMapper.writer();
    if (pretty) writer = writer.withDefaultPrettyPrinter();
    writer.writeValue(file.toFile(),object);
  }

  public JsonNode combineJsonFiles(List<Path> packageFiles) throws IOException {
    Preconditions.checkArgument(!packageFiles.isEmpty());
    JsonNode combined = jsonMapper.readValue(packageFiles.get(0).toFile(), JsonNode.class);
    for (int i = 1; i < packageFiles.size(); i++) {
      combined = jsonMapper.readerForUpdating(combined)
          .readValue(packageFiles.get(i).toFile());
    }
    return combined;
  }

}