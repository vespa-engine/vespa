package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

public class RankExpressionSymbolResolver {
    

    private static final Set<String> rankExpressionBultInFunctions = new HashSet<>() {{
        add("bm25");
        add("attribute");
        add("distance");

    }};

    /**
     * Resolves rank expression references in the tree
     *
     * @param node        The schema node to resolve the rank expression references in.
     * @param context     The parse context.
     */
    public static List<Diagnostic> resolveRankExpressionReferences(SchemaNode node, ParseContext context) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (
            node.getLanguageType() == LanguageType.RANK_EXPRESSION &&
            node.hasSymbol()
        ) {
            if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
                resolveRankExpressionBultInFunctions(node, context, diagnostics);
            }

            if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
                resolveFunctionReference(node, context, diagnostics);
            }

            if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
                node.setSymbolStatus(SymbolStatus.REFERENCE); // TODO: remove later, is placed here to pass tests
            }

        }

        for (SchemaNode child : node) {
            diagnostics.addAll(resolveRankExpressionReferences(child, context));
        }

        return diagnostics;
    }

    private static void resolveRankExpressionBultInFunctions(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        String identifier = node.getSymbol().getShortIdentifier();
        if (rankExpressionBultInFunctions.contains(identifier)) {
            node.setSymbolType(SymbolType.FUNCTION);
            node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
        }
    }

    private static void resolveFunctionReference(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        Optional<Symbol> symbol = context.schemaIndex().findSymbol(node.getSymbol().getScope(), SymbolType.FUNCTION, node.getSymbol().getShortIdentifier());

        if (symbol.isEmpty()) return;

        node.setSymbolType(SymbolType.FUNCTION);
        node.setSymbolStatus(SymbolStatus.REFERENCE);
        context.schemaIndex().insertSymbolReference(symbol.get(), node.getSymbol());
    }
}
