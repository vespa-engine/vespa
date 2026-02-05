package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class ArgumentUtils {
    /*
     * Modifies a node's symbol by setting a new type and status. 
     * If newType is null, symbol is removed entirely.
     * If newStatus is not equal to REFERENCE, any symbol is deleted from the reference index if it exists there.
     *
     * Symbols may be on a node or a depth-first descendant (following .get(0) recursively).
     */
    public static void modifyNodeSymbol(ParseContext context, RankNode node, SymbolType newType, SymbolStatus newStatus) {
        findRankNodeSymbol(node).ifPresent(symbol -> {
            if (newStatus != SymbolStatus.REFERENCE && symbol.getStatus() == SymbolStatus.REFERENCE) {
                context.schemaIndex().deleteSymbolReference(symbol);
            }

            if (newType != null) {
                symbol.setType(newType);
                symbol.setStatus(newStatus);
            } else {
                symbol.getNode().removeSymbol();
            }
        });
    }

    public static Optional<Symbol> findRankNodeSymbol(RankNode node) {
        Node ptr = node.getSchemaNode();
        for (;;) {
            if (ptr.hasSymbol()) return Optional.of(ptr.getSymbol());
            if (ptr.isLeaf()) break;
            ptr = ptr.get(0);
        }
        return Optional.empty();
    }

    /*
     * Removes any symbol registered on this node or on any depth-first descendant.
     */
    public static void removeNodeSymbols(ParseContext context, RankNode node) {
        modifyNodeSymbol(context, node, null, null);
    }
}
