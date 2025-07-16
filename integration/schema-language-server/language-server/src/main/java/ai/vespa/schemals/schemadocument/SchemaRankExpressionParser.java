package ai.vespa.schemals.schemadocument;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.IDENTIFIER_WITH_DASH;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.onnxModelInput;
import ai.vespa.schemals.parser.ast.consumedExpressionElm;
import ai.vespa.schemals.parser.ast.consumedFeatureListElm;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Node.LanguageType;

/**
 * SchemaRankExpressionParser is a parser for rank epxressions, which is small part in other files
 */
public class SchemaRankExpressionParser {
    static void printExpressionTree(PrintStream logger, ExpressionNode node, int indent) {
        String[] classList = node.getClass().toString().split("[.]");
        String className = classList[classList.length - 1];
        String msg = node.toString() + " (" + className + ")";

        if (node instanceof ReferenceNode) {
            var ref = ((ReferenceNode)node).reference();

            msg += " [REF: " + ref.name() + "]";

            if (ref.isIdentifier()) {
                msg += " [IDENTIFIER]";
            } 
            if (ref.output() != null) {
                msg += " [OUTPUT: " + ref.output() + "]";
            }
        }
        logger.println(new String(new char[indent]).replaceAll("\0", "\t") + msg);
        if (node instanceof CompositeNode) {
            for (var child : ((CompositeNode)node).children()) {
                printExpressionTree(logger, child, indent + 1);
            }
        }
    }

    static SchemaNode parseRankingExpression(ParseContext context, SchemaNode node, List<Diagnostic> diagnostics) {
        String expressionString = node.getRankExpressionString();

        Position expressionStart = node.getRange().getStart();
        
        if (expressionString == null)return null;
        CharSequence sequence = expressionString;

        RankingExpressionParser tolerantParser = new RankingExpressionParser(context.fileURI(), sequence);
        tolerantParser.setParserTolerant(true);

        try {
            if (node.isASTInstance(consumedExpressionElm.class)) {
                tolerantParser.expression();
            } else if (node.isASTInstance(consumedFeatureListElm.class)) {
                tolerantParser.featureList();
            } else {
                tolerantParser.rankPropertyFeature();
            }
        } catch (ai.vespa.schemals.parser.rankingexpression.ParseException pe) {
            // Ignore
        } catch (IllegalArgumentException ex) {
            // Ignore
        }

        if (tolerantParser.rootNode() == null) return null;
        return new SchemaNode(tolerantParser.rootNode(), expressionStart);
    }

    static void embedCST(ParseContext context, SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.containsOtherLanguageData(LanguageType.RANK_EXPRESSION)) return;
        // Don't confuse the parser by giving empty expression
        if (CSTUtils.rangeIsEmpty(node.getRange())) return;

        node.addChild(parseRankingExpression(context, node, diagnostics));
    }
}
