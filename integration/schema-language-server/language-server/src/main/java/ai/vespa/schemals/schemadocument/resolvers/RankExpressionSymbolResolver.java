package ai.vespa.schemals.schemadocument.resolvers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

public class RankExpressionSymbolResolver {
    

    private static final Set<String> rankExpressionBultInFunctions = new HashSet<>() {{
        add("query");
        add("bm25");
        add("attribute");
    }};

    /**
     * Resolves rank expression references in the tree
     *
     * @param node        The schema node to resolve the rank expression references in.
     * @param context     The parse context.
     * @param diagnostics The list to store any diagnostics encountered during resolution.
     */
    public static void resolveRankExpressionReferences(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        if (
            node.getLanguageType() == LanguageType.RANK_EXPRESSION &&
            node.hasSymbol() &&
            node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED
        ) {
            resolveRankExpressionBultInFunctions(node, context, diagnostics);


        }

        for (SchemaNode child : node) {
            resolveRankExpressionReferences(child, context, diagnostics);
        }
    }

    private static void resolveRankExpressionBultInFunctions(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        String identifier = node.getSymbol().getShortIdentifier();
        if (rankExpressionBultInFunctions.contains(identifier)) {
            node.setSymbolType(SymbolType.FUNCTION);
            node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
        }
    }
}
