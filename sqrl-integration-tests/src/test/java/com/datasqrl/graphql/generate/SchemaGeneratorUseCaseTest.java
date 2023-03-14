/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.graphql.generate;

import com.datasqrl.IntegrationTestSettings;
import com.datasqrl.util.StringUtil;
import com.datasqrl.util.TestScript;
import com.datasqrl.util.data.Nutshop;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;

@Slf4j
public class SchemaGeneratorUseCaseTest extends AbstractSchemaGeneratorTest {


  @BeforeEach
  public void setup(TestInfo testInfo) throws IOException {
    super.setup(testInfo);
  }

  @ParameterizedTest
  @ArgumentsSource(TestScript.AllScriptsProvider.class)
  public void fullScriptTest(TestScript script) {
    initialize(IntegrationTestSettings.getInMemory(), script.getRootPackageDirectory());
    snapshotTest(script.getScript());
  }

  @SneakyThrows
  protected String produceSchemaString(TestScript script) {
    initialize(IntegrationTestSettings.getInMemory(), script.getRootPackageDirectory());
    return generateSchema(script.getScript());
  }

  @Test
  @Disabled
  public void writeSchemaFile() {
    String schema = produceSchemaString(Nutshop.INSTANCE.getScripts().get(0));
    writeSchemaFile(Nutshop.INSTANCE.getScripts().get(0), schema);
  }

  @SneakyThrows
  private void writeSchemaFile(TestScript script, String schema) {
    String filename = script.getScriptPath().getFileName().toString();
    if (filename.endsWith(".sqrl")) {
      filename = StringUtil.removeFromEnd(filename, ".sqrl");
    }
    filename += ".schema.graphql";
    Path path = script.getScriptPath().getParent().resolve(filename);
    Files.writeString(path, schema);
  }

}