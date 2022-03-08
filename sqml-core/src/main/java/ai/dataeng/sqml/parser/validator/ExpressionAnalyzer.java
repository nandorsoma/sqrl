package ai.dataeng.sqml.parser.validator;

import ai.dataeng.sqml.type.Field;
import ai.dataeng.sqml.type.RelationType;
import ai.dataeng.sqml.type.Type;
import ai.dataeng.sqml.type.TypedField;
import ai.dataeng.sqml.type.basic.BooleanType;
import ai.dataeng.sqml.type.basic.DateTimeType;
import ai.dataeng.sqml.type.basic.IntegerType;
import ai.dataeng.sqml.type.basic.NullType;
import ai.dataeng.sqml.type.basic.NumberType;
import ai.dataeng.sqml.type.basic.StringType;
import ai.dataeng.sqml.tree.ArithmeticBinaryExpression;
import ai.dataeng.sqml.tree.AstVisitor;
import ai.dataeng.sqml.tree.BetweenPredicate;
import ai.dataeng.sqml.tree.BooleanLiteral;
import ai.dataeng.sqml.tree.ComparisonExpression;
import ai.dataeng.sqml.tree.DecimalLiteral;
import ai.dataeng.sqml.tree.DereferenceExpression;
import ai.dataeng.sqml.tree.DoubleLiteral;
import ai.dataeng.sqml.tree.EnumLiteral;
import ai.dataeng.sqml.tree.Expression;
import ai.dataeng.sqml.tree.FieldReference;
import ai.dataeng.sqml.tree.FunctionCall;
import ai.dataeng.sqml.tree.GenericLiteral;
import ai.dataeng.sqml.tree.Identifier;
import ai.dataeng.sqml.tree.InlineJoinBody;
import ai.dataeng.sqml.tree.IntervalLiteral;
import ai.dataeng.sqml.tree.IsEmpty;
import ai.dataeng.sqml.tree.IsNotNullPredicate;
import ai.dataeng.sqml.tree.LogicalBinaryExpression;
import ai.dataeng.sqml.tree.LongLiteral;
import ai.dataeng.sqml.tree.Node;
import ai.dataeng.sqml.tree.NodeRef;
import ai.dataeng.sqml.tree.NotExpression;
import ai.dataeng.sqml.tree.NullLiteral;
import ai.dataeng.sqml.tree.QualifiedName;
import ai.dataeng.sqml.tree.SimpleCaseExpression;
import ai.dataeng.sqml.tree.StringLiteral;
import ai.dataeng.sqml.tree.TimestampLiteral;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class ExpressionAnalyzer {

  public ExpressionAnalyzer() {
  }

  public ExpressionAnalysis analyze(Expression node, Scope scope) {
    ExpressionAnalysis analysis = new ExpressionAnalysis();
    Visitor visitor = new Visitor(analysis);
    node.accept(visitor, new Context(scope));
    return analysis;
  }

  public static class Context {
    private final Scope scope;

    public Context(Scope scope) {
      this.scope = scope;
    }

    public Scope getScope() {
      return scope;
    }
  }

  class Visitor extends AstVisitor<Type, Context> {
    private final ExpressionAnalysis analysis;

    public Visitor(ExpressionAnalysis analysis) {
      this.analysis = analysis;
    }

    @Override
    public Type visitNode(Node node, Context context) {
      throw new RuntimeException(String.format("Could not visit node: %s %s",
          node.getClass().getName(), node));
    }

    @Override
    public Type visitIdentifier(Identifier node, Context context) {
      //Todo: To Resolved Field
      //Todo: Fix shadowing
      List<FieldPath> fieldPath = context.getScope()
          .toFieldPath(QualifiedName.of(node));
      if (fieldPath.isEmpty()) {
        throw new RuntimeException(String.format("Could not resolve identifier %s", node));
      } else if (fieldPath.size() > 1) {
        throw new RuntimeException(String.format("Ambiguous identifier %s", node));
      }
      FieldPath fieldPath1 = fieldPath.get(0);

//      analysis.setFieldPath(node, fieldPath1);
      Type resolvedType = fieldPath1.getFields().get(fieldPath1.getFields().size() - 1).getType();
//      if (resolvedType.isEmpty()) {
//        throw new RuntimeException(String.format("Could not resolve identifier %s", node));
//      }
      return addType(node, resolvedType);
    }

    @Override
    public Type visitFieldReference(FieldReference node, Context context)
    {
      Field field = context.getScope().getRelation().getScalarFields().get(node.getFieldIndex());

      return addType(node, field.getType());
    }

    @Override
    public Type visitExpression(Expression node, Context context) {
      throw new RuntimeException(String.format("Expression needs type inference: %s. %s", 
          node.getClass().getName(), node));
    }

    @Override
    public Type visitIsNotNullPredicate(IsNotNullPredicate node, Context context) {
      node.getValue().accept(this, context);
      return addType(node, new BooleanType());
    }

    @Override
    public Type visitBetweenPredicate(BetweenPredicate node, Context context) {
      node.getValue().accept(this, context);
      return addType(node, new BooleanType());
    }

    @Override
    public Type visitLogicalBinaryExpression(LogicalBinaryExpression node, Context context) {
      node.getLeft().accept(this, context);
      node.getRight().accept(this, context);
      return addType(node, new BooleanType());
    }
//
//    @Override
//    public Type visitSubqueryExpression(SubqueryExpression node, Context context) {
//      StatementAnalyzer statementAnalyzer = new StatementAnalyzer(metadata,
//          planBuilder, node);
//      Scope scope = node.getQuery().accept(statementAnalyzer, context.getScope());
//
//      return scope.getRelation();
//    }

    @Override
    public Type visitComparisonExpression(ComparisonExpression node, Context context) {
      Type left = node.getLeft().accept(this, context);
      Type right = node.getRight().accept(this, context);

      //todo determine if compariable

      return addType(node, new BooleanType());
    }

    @Override
    public Type visitInlineJoinBody(InlineJoinBody node, Context context) {
      //Todo: Walk the join
//      RelationType rel = context.getScope().resolveRelation(node.getTable())
//          .orElseThrow(()-> new RuntimeException(String.format("Could not find relation %s %s", node.getTable(), node)));

//      if (node.getInverse().isPresent()) {
//        RelationType relationType = context.getScope().getRelation();
//        rel.addField(Field.newUnqualified(node.getInverse().get().toString(), relationType));
//      }

//      addRelation(node.getJoin(), rel);
//      return addType(node, rel);
      return null;
    }

    @Override
    public Type visitArithmeticBinary(ArithmeticBinaryExpression node, Context context) {
      return getOperator(context, node, OperatorType.valueOf(node.getOperator().name()), node.getLeft(), node.getRight());
    }

    @Override
    public Type visitFunctionCall(FunctionCall node, Context context) {
      //Todo: Function calls can accept a relation
//      Optional<FunctionDefinition> function = metadata.getFunctionProvider().resolve(node.getName());
//      if (function.isEmpty()) {
//        throw new RuntimeException(String.format("Could not find function %s", node.getName()));
//      }
//      //analysis.qualifyFunction(node, function.get());
//
//      TypeInference typeInference = function.get().getTypeInference();
//      //Todo: extend this for implicit type casting
//      Optional<Type> type = typeInference.getOutputTypeStrategy().inferType(null); //todo Call context
//      Preconditions.checkNotNull(type, "Function's output type could not be found. (no inference yet)");
//      for (Expression expression : node.getArguments()) {
//        expression.accept(this, context);
//      }

      return addType(node, IntegerType.INSTANCE);//type.get());
    }

    @Override
    public Type visitSimpleCaseExpression(SimpleCaseExpression node, Context context) {
      //todo case when
      return addType(node, new StringType());
    }

    @Override
    public Type visitNotExpression(NotExpression node, Context context) {
      return addType(node, new BooleanType());
    }

    @Override
    public Type visitDoubleLiteral(DoubleLiteral node, Context context) {
      return addType(node, new NumberType());
    }

    @Override
    public Type visitDecimalLiteral(DecimalLiteral node, Context context) {
      return addType(node, new NumberType());
    }

    @Override
    public Type visitGenericLiteral(GenericLiteral node, Context context) {
      throw new RuntimeException("Generic literal not supported yet.");
    }

    @Override
    public Type visitTimestampLiteral(TimestampLiteral node, Context context) {
      return addType(node, new DateTimeType());
    }

    @Override
    public Type visitIntervalLiteral(IntervalLiteral node, Context context) {
      return addType(node, new DateTimeType());
    }

    @Override
    public Type visitStringLiteral(StringLiteral node, Context context) {
      return addType(node, new StringType());
    }

    @Override
    public Type visitBooleanLiteral(BooleanLiteral node, Context context) {
      return addType(node, new BooleanType());
    }

    @Override
    public Type visitEnumLiteral(EnumLiteral node, Context context) {
      throw new RuntimeException("Enum literal not supported yet.");
    }

    @Override
    public Type visitNullLiteral(NullLiteral node, Context context) {
      return addType(node, new NullType());
    }

    @Override
    public Type visitLongLiteral(LongLiteral node, Context context) {
      return addType(node, new NumberType());
    }

    @Override
    public Type visitDereferenceExpression(DereferenceExpression node, Context context) {
      Type type = node.getBase().accept(this, context);
      if (!(type instanceof RelationType)) {
        throw new RuntimeException(String.format("Dereference type not a relation: %s", node));
      }
      RelationType<TypedField> relType = (RelationType<TypedField>) type;
      Optional<Field> field = relType.getField(node.getField().getValue());
      if (field.isEmpty()) {
        throw new RuntimeException(String.format("Could not dereference %s in %s", node.getBase(), node.getField()));
      }

      return addType(node, field.get().getType());
    }

    @Override
    public Type visitIsEmpty(IsEmpty node, Context context) {
      //tbd
      return addType(node, new StringType());
    }

    private Type getOperator(Context context, Expression node, OperatorType operatorType, Expression... arguments)
    {
      ImmutableList.Builder<Type> argumentTypes = ImmutableList.builder();
      for (Expression expression : arguments) {
        argumentTypes.add(process(expression, context));
      }
//
//      FunctionMetadata operatorMetadata;
//      try {
//        operatorMetadata = functionAndTypeManager.getFunctionMetadata(functionAndTypeManager.resolveOperator(operatorType, fromTypes(argumentTypes.build())));
//      }
//      catch (OperatorNotFoundException e) {
//        throw new SemanticException(TYPE_MISMATCH, node, "%s", e.getMessage());
//      }
//      catch (PrestoException e) {
//        if (e.getErrorCode().getCode() == StandardErrorCode.AMBIGUOUS_FUNCTION_CALL.toErrorCode().getCode()) {
//          throw new SemanticException(SemanticErrorCode.AMBIGUOUS_FUNCTION_CALL, node, e.getMessage());
//        }
//        throw e;
//      }
//
//      for (int i = 0; i < arguments.length; i++) {
//        Expression expression = arguments[i];
//        Type type = functionAndTypeManager.getType(operatorMetadata.getArgumentTypes().get(i));
//        coerceType(context, expression, type, format("Operator %s argument %d", operatorMetadata, i));
//      }
//
//      Type type = functionAndTypeManager.getType(operatorMetadata.getReturnType());
      return addType(node, new BooleanType());
    }
//
    private Type addType(Expression node, Type type) {
      analysis.putExpressionType(NodeRef.of(node), type);

      return type;
    }
//
//    private void addRelation(Relation relation, RelationType type) {
//      analysis.setRelation(relation, type);
//    }
  }
}
