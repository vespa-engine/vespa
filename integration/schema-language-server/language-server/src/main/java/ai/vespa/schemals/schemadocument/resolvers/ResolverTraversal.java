package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

/**
 * ResolverTraversal
 * Traversing the CST after initial parsing step and inheritance has been resolved to resolve symbol references etc.
 */
public class ResolverTraversal {
    public static List<Diagnostic> traverse(ParseContext context, SchemaNode CST) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        traverse(context, CST, diagnostics, false);
        return diagnostics;
    }

    private static void traverse(ParseContext context, SchemaNode currentNode, List<Diagnostic> diagnostics, boolean insideRankExpression) {

        if (currentNode.getLanguageType() == LanguageType.RANK_EXPRESSION && !insideRankExpression) {
            RankExpressionSymbolResolver.resolveRankExpression(currentNode, context);
        }

        if (currentNode.hasSymbol() && currentNode.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
            SymbolReferenceResolver.resolveSymbolReference(currentNode, context, diagnostics);
        }

        if (currentNode.containsOtherLanguageData(LanguageType.INDEXING)) {
            IndexingLanguageResolver.resolveIndexingLanguage(context, currentNode, diagnostics);
        }

        for (SchemaNode child : currentNode) {
            traverse(context, child, diagnostics, insideRankExpression || currentNode.getLanguageType() == LanguageType.RANK_EXPRESSION);
        }

        // Some things need run after the children has run.
        // If it becomes a lot, put into its own file
        // TODO: solution for field in struct
        if (currentNode.isASTInstance(fieldElm.class)) {
            ValidateFieldSettings.validateFieldSettings(context, currentNode, diagnostics);
        }
    }
}
