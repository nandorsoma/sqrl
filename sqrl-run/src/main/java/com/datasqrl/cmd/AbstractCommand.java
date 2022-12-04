package com.datasqrl.cmd;

import com.datasqrl.config.GlobalEngineConfiguration;
import com.datasqrl.config.provider.Dialect;
import com.datasqrl.config.provider.JDBCConnectionProvider;
import com.datasqrl.engine.database.relational.JDBCEngineConfiguration;
import com.datasqrl.engine.stream.flink.FlinkEngineConfiguration;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.error.ErrorPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import lombok.SneakyThrows;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractCommand implements Runnable {

    public static final Path DEFAULT_PACKAGE = Path.of("package.json");
    public static final String DEFAULT_DB_NAME = "datasqrl";

    @CommandLine.ParentCommand
    protected RootCommand root;

    protected JDBCConnectionProvider jdbcConnection;

    @SneakyThrows
    public void run() {
        ErrorCollector collector = ErrorCollector.root();
        try {
            runCommand(collector);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(ErrorPrinter.prettyPrint(collector));
    }

    protected abstract void runCommand(ErrorCollector errors) throws Exception;

    public List<Path> findPackageFiles() {
        if (root.packageFiles.isEmpty()) {
            if (Files.isRegularFile(DEFAULT_PACKAGE)) {
                return List.of(DEFAULT_PACKAGE);
            }
        }
        return root.packageFiles;
    }

    public List<Path> getPackageFilesWithDefault(boolean execute) throws IOException {
        List<Path> packageFiles = findPackageFiles();
        GlobalEngineConfiguration engineConfig = new GlobalEngineConfiguration();
        if (!packageFiles.isEmpty()) {
            engineConfig = GlobalEngineConfiguration.readFrom(packageFiles,GlobalEngineConfiguration.class);
        }
        if (engineConfig.getEngines()==null || engineConfig.getEngines().isEmpty()) {
            packageFiles = new ArrayList<>(packageFiles);
            packageFiles.add(writeDefaultEngineConfig(execute));
        } else {
            //Extract JDBC engine
            JDBCEngineConfiguration jdbcConfig = Iterables.getOnlyElement(Iterables.filter(engineConfig.getEngines(),JDBCEngineConfiguration.class));
            jdbcConnection = jdbcConfig.getConnectionProvider();
        }
        return packageFiles;
    }

    @SneakyThrows
    private Path writeDefaultEngineConfig(boolean execute) {
        JDBCEngineConfiguration jdbcEngineConfiguration;
        if (execute) {
            PostgreSQLContainer postgreSQLContainer = startPostgres();
            jdbcEngineConfiguration = JDBCEngineConfiguration.builder()
                    .dbURL(postgreSQLContainer.getJdbcUrl())
                    .host(postgreSQLContainer.getHost())
                    .user(postgreSQLContainer.getUsername())
                    .port(postgreSQLContainer.getMappedPort(5432))
                    .dialect(Dialect.POSTGRES)
                    .password(postgreSQLContainer.getPassword())
                    .database(postgreSQLContainer.getDatabaseName())
                    .driverName(postgreSQLContainer.getDriverClassName())
                    .build();
            jdbcConnection = jdbcEngineConfiguration.getConnectionProvider();
        } else {
            jdbcEngineConfiguration = JDBCEngineConfiguration.builder()
                    .dbURL("invalid")
                    .dialect(Dialect.POSTGRES)
                    .database(DEFAULT_DB_NAME)
                    .build();
        }

        FlinkEngineConfiguration flinkEngineConfiguration =
                FlinkEngineConfiguration.builder()
                        .savepoint(false)
                        .build();

        Path enginesFile = Files.createTempFile("package-engines", ".json");
        File file = enginesFile.toFile();
        file.deleteOnExit();

        ObjectMapper mapper = new ObjectMapper();
        String enginesConf = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of(GlobalEngineConfiguration.ENGINES_PROPERTY,
                        List.of(flinkEngineConfiguration
                                , jdbcEngineConfiguration
                        )));

        Files.write(enginesFile, enginesConf.getBytes(StandardCharsets.UTF_8));
        return enginesFile;
    }

    private PostgreSQLContainer startPostgres() {
        DockerImageName image = DockerImageName.parse("postgres:14.2");
        PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(image)
                .withDatabaseName(DEFAULT_DB_NAME);
        postgreSQLContainer.start();
        return postgreSQLContainer;
    }

}