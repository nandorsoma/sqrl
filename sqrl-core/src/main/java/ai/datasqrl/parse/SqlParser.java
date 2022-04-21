/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.datasqrl.parse;

import static java.util.Objects.requireNonNull;

import ai.datasqrl.parse.tree.Node;
import ai.datasqrl.parse.tree.ScriptNode;
import ai.datasqrl.parse.tree.SqrlStatement;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;

class SqlParser {

  private static final BaseErrorListener LEXER_ERROR_LISTENER = new BaseErrorListener() {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
        int charPositionInLine, String message, RecognitionException e) {
      throw new ParsingException(message, e, line, charPositionInLine);
    }
  };
  private static final BiConsumer<SqlBaseLexer, SqlBaseParser> DEFAULT_PARSER_INITIALIZER = (SqlBaseLexer lexer, SqlBaseParser parser) -> {
  };

  private static final ErrorHandler PARSER_ERROR_HANDLER = ErrorHandler.builder()
      .specialRule(SqlBaseParser.RULE_expression, "<expression>")
      .specialRule(SqlBaseParser.RULE_booleanExpression, "<expression>")
      .specialRule(SqlBaseParser.RULE_valueExpression, "<expression>")
      .specialRule(SqlBaseParser.RULE_primaryExpression, "<expression>")
      .specialRule(SqlBaseParser.RULE_identifier, "<identifier>")
      .specialRule(SqlBaseParser.RULE_string, "<string>")
      .specialRule(SqlBaseParser.RULE_query, "<query>")
      .specialRule(SqlBaseParser.RULE_type, "<type>")
      .specialToken(SqlBaseLexer.INTEGER_VALUE, "<integer>")
//      .ignoredRule(ai.datasqrl.sqml.parser.SqlBaseParser.RULE_nonReserved)
      .build();

  private final BiConsumer<SqlBaseLexer, SqlBaseParser> initializer;
  private final boolean enhancedErrorHandlerEnabled;

  public SqlParser(SqlParserOptions options) {
    this(options, DEFAULT_PARSER_INITIALIZER);
  }

  public SqlParser(SqlParserOptions options, BiConsumer<SqlBaseLexer, SqlBaseParser> initializer) {
    this.initializer = requireNonNull(initializer, "initializer is null");
    requireNonNull(options, "options is null");
    enhancedErrorHandlerEnabled = options.isEnhancedErrorHandlerEnabled();
  }

  public ScriptNode createScript(String sql, ParsingOptions parsingOptions) {
    return (ScriptNode) invokeParser("script", sql, SqlBaseParser::script,
        parsingOptions);
  }

  public SqrlStatement createStatement(String sql, ParsingOptions parsingOptions) {
    return (SqrlStatement) invokeParser("statement", sql, SqlBaseParser::singleStatement,
        parsingOptions);
  }

  private Node invokeParser(String name, String sql,
      Function<SqlBaseParser, ParserRuleContext> parseFunction, ParsingOptions parsingOptions) {
    try {
      SqlBaseLexer lexer = new SqlBaseLexer(new CaseInsensitiveStream(CharStreams.fromString(sql)));
      CommonTokenStream tokenStream = new CommonTokenStream(lexer);
      SqlBaseParser parser = new SqlBaseParser(tokenStream);
      initializer.accept(lexer, parser);

      // Override the default error strategy to not attempt inserting or deleting a token.
      // Otherwise, it messes up error reporting
      parser.setErrorHandler(new DefaultErrorStrategy() {
        @Override
        public Token recoverInline(Parser recognizer)
            throws RecognitionException {
          if (nextTokensContext == null) {
            throw new InputMismatchException(recognizer);
          } else {
            throw new InputMismatchException(recognizer, nextTokensState, nextTokensContext);
          }
        }
      });

      parser.addParseListener(new PostProcessor(Arrays.asList(parser.getRuleNames()),
          parsingOptions.getWarningConsumer()));

      lexer.removeErrorListeners();
      lexer.addErrorListener(LEXER_ERROR_LISTENER);

      parser.removeErrorListeners();

      if (enhancedErrorHandlerEnabled) {
        parser.addErrorListener(PARSER_ERROR_HANDLER);
      } else {
        parser.addErrorListener(LEXER_ERROR_LISTENER);
      }

      ParserRuleContext tree;
      parser.getInterpreter().setPredictionMode(PredictionMode.LL);
      tree = parseFunction.apply(parser);

      return new AstBuilder(parsingOptions).visit(tree);
    } catch (StackOverflowError e) {
      throw new ParsingException(name + " is too large (stack overflow while parsing)");
    }
  }

  private class PostProcessor
      extends SqlBaseBaseListener {

    private final List<String> ruleNames;
    private final Consumer<ParsingWarning> warningConsumer;

    public PostProcessor(List<String> ruleNames, Consumer<ParsingWarning> warningConsumer) {
      this.ruleNames = ruleNames;
      this.warningConsumer = requireNonNull(warningConsumer, "warningConsumer is null");
    }
//
//    @Override
//    public void exitUnquotedIdentifier(SqlBaseParser.UnquotedIdentifierContext context) {
////      String identifier = context.IDENTIFIER().getText();
////      for (IdentifierSymbol identifierSymbol : EnumSet.complementOf(allowedIdentifierSymbols)) {
////        char symbol = identifierSymbol.getSymbol();
////        if (identifier.indexOf(symbol) >= 0) {
////          throw new ParsingException(
////              "identifiers must not contain '" + identifierSymbol.getSymbol() + "'", null,
////              context.IDENTIFIER().getSymbol().getLine(),
////              context.IDENTIFIER().getSymbol().getCharPositionInLine());
////        }
////      }
//    }
//
//    @Override
//    public void exitBackQuotedIdentifier(SqlBaseParser.BackQuotedIdentifierContext context) {
//      Token token = context.BACKQUOTED_IDENTIFIER().getSymbol();
//      throw new ParsingException(
//          "backquoted identifiers are not supported; use double quotes to quote identifiers",
//          null,
//          token.getLine(),
//          token.getCharPositionInLine());
//    }
//
//    @Override
//    public void exitDigitIdentifier(SqlBaseParser.DigitIdentifierContext context) {
//      Token token = context.DIGIT_IDENTIFIER().getSymbol();
//      throw new ParsingException(
//          "identifiers must not start with a digit; surround the identifier with double quotes",
//          null,
//          token.getLine(),
//          token.getCharPositionInLine());
//    }
//
//    @Override
//    public void exitNonReserved(SqlBaseParser.NonReservedContext context) {
//      // we can't modify the tree during rule enter/exit event handling unless we're dealing with a terminal.
//      // Otherwise, ANTLR gets confused an fires spurious notifications.
//      if (!(context.getChild(0) instanceof TerminalNode)) {
//        int rule = ((ParserRuleContext) context.getChild(0)).getRuleIndex();
//        throw new AssertionError(
//            "nonReserved can only contain tokens. Found nested rule: " + ruleNames.get(rule));
//      }
//
//      // replace nonReserved words with IDENT tokens
//      context.getParent().removeLastChild();
//
//      Token token = (Token) context.getChild(0).getPayload();
//      if (SqlParserOptions.RESERVED_WORDS_WARNING.contains(token.getText().toUpperCase())) {
//        warningConsumer.accept(new ParsingWarning(
//            format(
//                "%s should be a reserved word, please use double quote (\"%s\"). This will be made a reserved word in future release.",
//                token.getText(), token.getText()),
//            token.getLine(),
//            token.getCharPositionInLine()));
//      }
//
//      context.getParent().addChild(new CommonToken(
//          new Pair<>(token.getTokenSource(), token.getInputStream()),
//          ai.datasqrl.sqml.parser.SqlBaseLexer.IDENTIFIER,
//          token.getChannel(),
//          token.getStartIndex(),
//          token.getStopIndex()));
//    }
  }
}
