package com.datasqrl.cmd;

import static com.datasqrl.config.ConnectorFactoryFactory.PRINT_SINK_NAME;
import static com.datasqrl.packager.Packager.*;
import static com.datasqrl.util.NameUtil.namepath2Path;

import com.datasqrl.canonicalizer.Name;
import com.datasqrl.canonicalizer.NamePath;
import com.datasqrl.config.PackageJson.DependenciesConfig;
import com.datasqrl.config.Dependency;
import com.datasqrl.config.PackageJson;
import com.datasqrl.config.PackageJson.ScriptConfig;
import com.datasqrl.config.SqrlConfigCommons;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.error.ErrorPrefix;
import com.datasqrl.loaders.StandardLibraryLoader;
import com.datasqrl.packager.ImportExportAnalyzer;
import com.datasqrl.packager.Packager;
import com.datasqrl.packager.repository.Repository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class PackageBootstrap {
  Repository repository;
  ErrorCollector errors;

  @SneakyThrows
  public PackageJson bootstrap(Path rootDir, List<Path> packageFiles,
      String[] profiles, Path[] files,  Function<ErrorCollector, PackageJson> defaultConfigFnc) {
    ErrorCollector errors = this.errors.withLocation(ErrorPrefix.CONFIG).resolve("package");

    //Create build dir to unpack resolved dependencies
    Path buildDir = rootDir.resolve(Packager.BUILD_DIR_NAME);
    Packager.cleanBuildDir(buildDir);
    Packager.createBuildDir(buildDir);

    Optional<List<Path>> existingPackage = Packager.findPackageFile(rootDir, packageFiles);
    Optional<PackageJson> existingConfig;
    existingConfig = existingPackage.map(
        paths -> SqrlConfigCommons.fromFilesPackageJson(errors, paths));

    Map<String, Dependency> dependencies = new HashMap<>();
    // Check if 'profiles' key is set, replace if existing
    if (existingConfig.isPresent() && existingConfig.get().hasProfileKey()) {
      profiles = existingConfig.get().getProfiles().toArray(String[]::new);
    }

    //Create package.json from project root if exists
    List<Path> configFiles = new ArrayList<>();

    if (existingConfig.isEmpty() && profiles.length == 0) { //No profiles found, use default (remotely downloaded)
      PackageJson defaultConfig = defaultConfigFnc.apply(errors);
      Path path = buildDir.resolve(PACKAGE_JSON);
      profiles = defaultConfig.getProfiles().toArray(String[]::new);
      existingConfig = Optional.of(defaultConfig);
      defaultConfig.toFile(path, true);
      configFiles.add(path);
    }

    //Download any profiles
    for (String profile : profiles) {
      if (isLocalProfile(rootDir, profile)) {
        Path localProfile = rootDir.resolve(profile).resolve(PACKAGE_JSON);
        configFiles.add(localProfile);
      } else {
        //check to see if it's already in the package json, download the correct dep
        Optional<Dependency> dependency;
        if (hasVersionedProfileDependency(existingConfig, profile)) {
          dependency = existingConfig.get().getDependencies().getDependency(profile);
        } else {
          dependency = repository.resolveDependency(profile);
        }
        Path profilePath = namepath2Path(rootDir.resolve(BUILD_DIR_NAME), NamePath.parse(profile));

        if (dependency.isPresent()) {
          boolean success = repository.retrieveDependency(profilePath,
              dependency.get());
          if (success) {
            dependencies.put(profile, dependency.get());
          } else {
            throw new RuntimeException("Could not retrieve profile dependency: " + profile);
          }
        } else {
          throw new RuntimeException("Could not find profile in repository: " + profile);
        }

        Path remoteProfile = profilePath.resolve(PACKAGE_JSON);
        if (Files.isRegularFile(remoteProfile)) {
          configFiles.add(remoteProfile);
        } else {
          throw new RuntimeException("Could not find package.json in profile: " + profile);
        }
      }
    }

    existingPackage.ifPresent(configFiles::addAll);

    // Could not find any package json
    if (configFiles.isEmpty()) {
      throw new RuntimeException("Could not find package.json");
    }

    // Merge all configurations
    PackageJson packageJson = SqrlConfigCommons.fromFilesPackageJson(errors, configFiles);
    packageJson.setProfiles(profiles);

    //Add dependencies of discovered profiles
    dependencies.forEach((key, dep) -> {
      DependenciesConfig dependenciesConfig = packageJson.getDependencies();
      dependenciesConfig.addDependency(key, dep);
    });

    //Override main and graphql if they are specified as command line arguments
    Optional<Path> mainScript = (files.length > 0 && files[0].getFileName().toString().toLowerCase().endsWith(".sqrl")) ? Optional.of(files[0]) : Optional.empty();
    Optional<Path> graphQLSchemaFile = (files.length > 1) ? Optional.of(files[1]) : Optional.empty();

    ScriptConfig scriptConfig = packageJson.getScriptConfig();
    boolean isMainScriptSet = scriptConfig.getMainScript().isPresent();
    boolean isGraphQLSet = scriptConfig.getGraphql().isPresent();

    // Set main script if not already set and if it's a regular file
    if (mainScript.isPresent() && Files.isRegularFile(relativize(rootDir, mainScript))) {
      scriptConfig.setMainScript(mainScript.get().toString());
    } else if (!isMainScriptSet && mainScript.isPresent()) {
      errors.fatal("Main script is not a regular file: %s", mainScript.get());
    } else if (!isMainScriptSet && files.length > 0) {
      errors.fatal("Main script is not a sqrl script: %s", files[0].getFileName().toString());
    } else if (!isMainScriptSet && mainScript.isEmpty()){
      errors.fatal("No main sqrl script specified");
    }

    // Set GraphQL schema file if not already set and if it's a regular file
    if (graphQLSchemaFile.isPresent() && Files.isRegularFile(relativize(rootDir, graphQLSchemaFile))) {
      scriptConfig.setGraphql(graphQLSchemaFile.get().toString());
    } else if (!isGraphQLSet && graphQLSchemaFile.isPresent()) {
      errors.fatal("GraphQL schema file is not a regular file: %s", graphQLSchemaFile.get());
    }

    return packageJson;
  }

  /**
   * We want to guard against misspelling so we can throw sensible error messages
   */
  public static boolean isLocalProfile(Path rootDir, String profile) {
    //1. Check if it's on the local file system
    if (Files.isDirectory(rootDir.resolve(profile))) {
      //1. Profile must contain a package json
      if (!Files.isRegularFile(rootDir.resolve(profile).resolve(PACKAGE_JSON))) {
        log.info("Profile [" + profile + "] is a directory but missing a package.json. Attempting to resolve as a remote profile.");
        return false;
      }

      return true;
    }
    //2. Check if it looks like a repo link
    if (Pattern.matches("^\\w+(?:\\.\\w+)+$", profile)) {
      return false;
    }

    throw new RuntimeException(
        String.format("Unknown profile format [%s]. It must be either be a filesystem folder or a repository name.",
            profile));
  }

  private Path relativize(Path rootDir, Optional<Path> path) {
    return path.get().isAbsolute() ? path.get() : rootDir.resolve(path.get());
  }

  private boolean hasVersionedProfileDependency(Optional<PackageJson> existingConfig, String profile) {
    return existingConfig.isPresent()
        && existingConfig.get().getDependencies().getDependency(profile).isPresent()
        && existingConfig.get().getDependencies().getDependency(profile).get().getVersion().isPresent();
  }
}