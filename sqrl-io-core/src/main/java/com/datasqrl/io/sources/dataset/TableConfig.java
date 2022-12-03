package com.datasqrl.io.sources.dataset;

import com.datasqrl.config.constraints.OptionalMinString;
import com.datasqrl.config.error.ErrorCollector;
import com.datasqrl.config.util.ConfigurationUtil;
import com.datasqrl.io.sources.SharedConfiguration;
import com.datasqrl.io.sources.DataSystemConnector;
import com.datasqrl.io.sources.DataSystemConnectorConfig;
import com.datasqrl.parse.tree.name.Name;
import com.datasqrl.parse.tree.name.NamePath;
import com.datasqrl.schema.input.FlexibleDatasetSchema;
import com.datasqrl.schema.input.SchemaAdjustmentSettings;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Optional;

@SuperBuilder
@NoArgsConstructor
@Getter
public class TableConfig extends SharedConfiguration implements Serializable {

    @NonNull @NotNull
    @Size(min = 3)
    String name;
    @OptionalMinString
    String identifier;
    @Valid @NonNull @NotNull
    DataSystemConnectorConfig connector;

    /**
     * TODO: make this configurable
     * @return
     */
    @JsonIgnore
    public SchemaAdjustmentSettings getSchemaAdjustmentSettings() {
        return SchemaAdjustmentSettings.DEFAULT;
    }

    private DataSystemConnector baseInitialize(ErrorCollector errors, NamePath basePath) {
        if (!Name.validName(name)) {
            errors.fatal("Table needs to have valid name: %s", name);
            return null;
        }
        errors = errors.resolve(name);
        if (!rootInitialize(errors)) return null;
        if (!ConfigurationUtil.javaxValidate(this, errors)) {
            return null;
        }

        if (Strings.isNullOrEmpty(identifier)) {
            identifier = name;
        }
        identifier = getCanonicalizer().getCanonicalizer().getCanonical(identifier);

        if (!format.initialize(errors.resolve("format"))) return null;

        DataSystemConnector connector = this.connector.initialize(errors.resolve(name).resolve("datasource"));
        if (connector == null) return null;
        if (connector.requiresFormat(getType()) && getFormat()==null) {
            errors.fatal("Need to configure a format");
            return null;
        }
        return connector;
    }

    public TableSource initializeSource(ErrorCollector errors, NamePath basePath,
                                        FlexibleDatasetSchema.TableField schema) {
        DataSystemConnector connector = baseInitialize(errors,basePath);
        if (connector==null) return null;
        Preconditions.checkArgument(getType().isSource());
        Name tableName = getResolvedName();
        return new TableSource(connector,this,basePath.concat(tableName), tableName, schema);
    }

    public TableInput initializeInput(ErrorCollector errors, NamePath basePath) {
        DataSystemConnector connector = baseInitialize(errors,basePath);
        if (connector==null) return null;
        Preconditions.checkArgument(getType().isSource());
        Name tableName = getResolvedName();
        return new TableInput(connector,this,basePath.concat(tableName), tableName);
    }

    public TableSink initializeSink(ErrorCollector errors, NamePath basePath,
                                    Optional<FlexibleDatasetSchema.TableField> schema) {
        DataSystemConnector connector = baseInitialize(errors,basePath);
        if (connector==null) return null;
        Preconditions.checkArgument(getType().isSink());
        Name tableName = getResolvedName();
        return new TableSink(connector, this, basePath.concat(tableName), tableName, schema);
    }

    @JsonIgnore
    public Name getResolvedName() {
        return Name.of(name,getCanonicalizer().getCanonicalizer());
    }

    public static TableConfigBuilder copy(SharedConfiguration config) {
        return TableConfig.builder()
                .type(config.getType())
                .canonicalizer(config.getCanonicalizer())
                .charset(config.getCharset())
                .format(config.getFormat());
    }

}