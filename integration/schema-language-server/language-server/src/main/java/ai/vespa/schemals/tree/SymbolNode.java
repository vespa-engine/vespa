package ai.vespa.schemals.tree;

import ai.vespa.schemals.parser.Token.TokenType;

public class SymbolNode extends SchemaNode {

    private TokenType symbolType;

    public SymbolNode(SchemaNode node, TokenType symbolType) {
        super(node);
        this.symbolType = symbolType;
    }

    public TokenType getSymbolType() { return symbolType; }
}
