package ai.vespa.schemals.tree;

import ai.vespa.schemals.parser.Token.TokenType;

public class SymbolReferenceNode extends SymbolNode {

    public SymbolReferenceNode(SchemaNode node, TokenType symbolType) {
        super(node, symbolType);
    }
}
