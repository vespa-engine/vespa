package ai.vespa.schemals.schemadocument;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.consumedExpressionElm;
import ai.vespa.schemals.parser.ast.consumedFeatureListElm;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Parser for ranking expressions.
 */
public class SchemaRankingExpressionParser {

    /**
     * Assumes that node.getRankingExpressionString() is never null
     */
    private static SchemaNode parseRankingExpression(ParseContext context, SchemaNode node, List<Diagnostic> diagnostics) {
        String expressionString = node.getRankingExpressionString();

        RankingExpressionParser tolerantParser = new RankingExpressionParser(context.fileURI(), expressionString);
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
        return new SchemaNode(tolerantParser.rootNode(), node.getRange().getStart());
    }

    /**
     * Takes a node containing ranking expression data, parses the data and adds the resulting tree
     * as a child to the given node.
     *
     * Assumes that <pre><code>
     * node.containsOtherLanguageData(LanguageType.RANK_EXPRESSION)
     * </code></pre> is true.
     * 
     * @param context
     * @param node CST node containing ranking expression data.
     * @param diagnostics
     *
     */
    static void embedCST(ParseContext context, SchemaNode node, List<Diagnostic> diagnostics) {
        // Don't confuse the parser by giving empty expression
        if (CSTUtils.rangeIsEmpty(node.getRange())) return;

        node.addChild(parseRankingExpression(context, node, diagnostics));
    }
}
