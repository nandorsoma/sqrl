package ai.dataeng.sqml.logical4;

import ai.dataeng.sqml.logical4.LogicalPlan.RowNode;
import ai.dataeng.sqml.relation.RowExpression;
import lombok.Getter;

@Getter
public class FilterOperator extends LogicalPlan.RowNode<LogicalPlan.RowNode> {

    LogicalPlan.RowNode input;
    RowExpression predicate;

    public FilterOperator(RowNode input, RowExpression predicate) {
        super(input);
    }

    @Override
    public LogicalPlan.Column[][] getOutputSchema() {
        //Filters do not change the records or their schema
        return getInput().getOutputSchema();
    }
}
