package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

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
     *
     * Returns the leaf on the depth-first path from node.
     */
    public static Node modifyNodeSymbol(ParseContext context, RankNode node, SymbolType newType, SymbolStatus newStatus) {
        Node leaf = node.getSchemaNode();
        while (leaf.size() > 0) {
            leaf = leaf.get(0);

            if (leaf.hasSymbol()) {
                Symbol symbol = leaf.getSymbol();

                if (newStatus != SymbolStatus.REFERENCE && symbol.getStatus() == SymbolStatus.REFERENCE) {
                    context.schemaIndex().deleteSymbolReference(symbol);
                }

                if (newType != null) {
                    symbol.setType(newType);
                    symbol.setStatus(newStatus);
                } else {
                    leaf.removeSymbol();
                }
            }
        }

        return leaf;
    }

    /*
     * Removes any symbol registered on this node or on any depth-first descendant.
     *
     * Returns the leaf on the depth-first path from node.
     */
    public static Node removeNodeSymbols(ParseContext context, RankNode node) {
        return modifyNodeSymbol(context, node, null, null);
    }
}
