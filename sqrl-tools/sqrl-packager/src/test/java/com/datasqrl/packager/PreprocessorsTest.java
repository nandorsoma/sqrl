package com.datasqrl.packager;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasqrl.config.PackageJsonImpl;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.packager.preprocess.Preprocessor;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PreprocessorsTest {

  @InjectMocks
  private Preprocessors preprocessors;

  @Mock
  private Preprocessor firstPreprocessor;
  @Mock
  private Preprocessor secondPreprocessor;

  private Preprocessors.PreprocessorsContext context;
  private Path rootDir = Paths.get("/test");
  private Path buildDir = Paths.get("/build");

  @SneakyThrows
  @BeforeEach
  public void setUp() {
    Set<Preprocessor> preprocessorSet = new HashSet<>();
    preprocessorSet.add(firstPreprocessor);
    preprocessorSet.add(secondPreprocessor);
    preprocessors = new Preprocessors(preprocessorSet);

    context = Preprocessors.PreprocessorsContext.builder()
        .rootDir(rootDir)
        .buildDir(buildDir)
        .config(new PackageJsonImpl())
        .profiles(new String[]{})
        .errors(ErrorCollector.root())
        .build();
  }

  @Test
  public void testMultiplePreprocessorsForSingleFile() {
    Path fileToProcess = Paths.get("/test/src/File.java");
    when(firstPreprocessor.getPattern()).thenReturn(Pattern.compile(".*\\.java"));
    when(secondPreprocessor.getPattern()).thenReturn(Pattern.compile("File.*"));

    preprocessors.processUserFiles(Stream.of(fileToProcess).collect(Collectors.toList()), context);

    verify(firstPreprocessor).processFile(eq(fileToProcess), any(), any());
    verify(secondPreprocessor).processFile(eq(fileToProcess), any(), any());
  }

  @SneakyThrows
  @Test
  public void testExcludedDirectories() {
    Path includedFile = Paths.get("/test/src/File.java");
    Path excludedDirBuild = Paths.get("/test/build");
    Path excludedDirFile = Paths.get("/test/build/File.java");

    when(firstPreprocessor.getPattern()).thenReturn(Pattern.compile(".*\\.java"));
    when(secondPreprocessor.getPattern()).thenReturn(Pattern.compile("File.*"));

    try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
      // Mock the behavior of Files.walk
      mockedFiles.when(() -> Files.walk(rootDir, FileVisitOption.FOLLOW_LINKS))
          .thenReturn(Stream.of(excludedDirBuild, excludedDirFile, includedFile));

      // Mock Files.isRegularFile to return true for includedFile and false for directories
      mockedFiles.when(() -> Files.isRegularFile(includedFile)).thenReturn(true);
      mockedFiles.when(() -> Files.isRegularFile(excludedDirBuild)).thenReturn(false);
      mockedFiles.when(() -> Files.isRegularFile(excludedDirFile)).thenReturn(false);

      // Call the handle method
      assertTrue(preprocessors.handle(context));

      // Verify that preprocessors are never called for the paths in excluded directories
      verify(firstPreprocessor, never()).processFile(eq(excludedDirFile), any(), any());
      verify(secondPreprocessor, never()).processFile(eq(excludedDirFile), any(), any());

      // Verify that preprocessors are called for the included file
      verify(firstPreprocessor, times(1)).processFile(eq(includedFile), any(), any());
      verify(secondPreprocessor, times(1)).processFile(eq(includedFile), any(), any());
    }
  }
}
