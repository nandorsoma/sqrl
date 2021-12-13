package ai.dataeng.sqml.logical4;

import ai.dataeng.sqml.ingest.schema.FlexibleDatasetSchema;
import ai.dataeng.sqml.ingest.schema.SchemaAdjustmentSettings;
import ai.dataeng.sqml.ingest.source.SourceTable;
import ai.dataeng.sqml.relation.VariableReferenceExpression;
import ai.dataeng.sqml.tree.name.NamePath;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents an event source of document records.
 */
@Value
public class DocumentSource extends LogicalPlan.DocumentNode<LogicalPlan.Node> {

    @NonNull
    final FlexibleDatasetSchema.TableField sourceSchema;
    @NonNull
    final SourceTable table;
    @NonNull
    final Map<NamePath, LogicalPlan.Column[]> outputSchema;

    //TODO: Users should be able to control this via global config or hints
    @NonNull
    final SchemaAdjustmentSettings settings = SchemaAdjustmentSettings.DEFAULT;

    public DocumentSource(FlexibleDatasetSchema.TableField sourceSchema, @NonNull SourceTable table, @NonNull Map<NamePath, LogicalPlan.Column[]> outputSchema) {
        super(Collections.EMPTY_LIST);
        this.sourceSchema = sourceSchema;
        this.table = table;
        this.outputSchema = outputSchema;
    }

    @Override
    public Map<NamePath, LogicalPlan.Column[]> getOutputSchema() {
        return outputSchema;
    }




}