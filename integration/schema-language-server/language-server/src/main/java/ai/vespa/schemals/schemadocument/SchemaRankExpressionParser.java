package ai.vespa.schemals.schemadocument;

import java.util.ArrayList;
import java.util.HashSet;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;

public class SchemaRankExpressionParser {

    private static HashSet<TokenType> multilineTokens = new HashSet<>() {{
        add(TokenType.EXPRESSION_ML);
        add(TokenType.SUMMARYFEATURES_ML);
        add(TokenType.SUMMARYFEATURES_ML_INHERITS);
        add(TokenType.MATCHFEATURES_ML);
        add(TokenType.MATCHFEATURES_ML_INHERITS);
        add(TokenType.RANKFEATURES_ML);
    }};

    private static Position findExpressionOffset(SchemaNode node) {

        boolean isMultiline = multilineTokens.contains(node.findFirstLeaf().getSchemaType());

        char splitChar = isMultiline ? '{' : ':';

        int offset = node.getText().indexOf(splitChar) + 1;

        String preString = node.getText().substring(0, offset);

        long numberOfNewLines = preString.chars().filter(ch -> ch == '\n').count();

        if (numberOfNewLines > 0) {
            offset = preString.length() - preString.lastIndexOf('\n');
        }

        return new Position(
            (int)numberOfNewLines,
            offset
        );
    }

    static SchemaNode wrapNodes(ai.vespa.schemals.parser.rankingexpression.Node rootNode) {
        return new SchemaNode(rootNode);
    }

    static SchemaNode parseRankingExpression(ParseContext context, SchemaNode node, ArrayList<Diagnostic> diagnostics) {
        String expressionString = node.getRankExpressionString();
        Position expressionOffset = findExpressionOffset(node);

        Position expressionStart = CSTUtils.addPositions(node.getRange().getStart(), expressionOffset);
        
        if (expressionString == null)return null;
        CharSequence sequence = expressionString;

        RankingExpressionParser parser = new RankingExpressionParser(context.logger(), context.fileURI(), sequence);
        parser.setParserTolerant(false);

        try {
            if (node.containsExpressionData()) {
                parser.expression();
            } else {
                parser.featureList();
            }

            return wrapNodes(parser.rootNode());
        } catch(ai.vespa.schemals.parser.rankingexpression.ParseException pe) {

            Range range = RankingExpressionUtils.getNodeRange(pe.getToken());
            
            range = CSTUtils.addPositionToRange(expressionStart, range);

            diagnostics.add(new Diagnostic(range, pe.getMessage()));

        } catch(IllegalArgumentException ex) {
            // TODO: test this
            diagnostics.add(new Diagnostic(node.getRange(), ex.getMessage(), DiagnosticSeverity.Error, ""));
        }

        return null;
    }
}
