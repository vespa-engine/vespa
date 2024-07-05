package ai.vespa.schemals.context;

import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.tree.SymbolDefinitionNode;

public class Symbol {
    private Token.TokenType type;
    private SymbolDefinitionNode identifierNode;
    private Symbol scope = null;

    public Symbol(Token.TokenType type, SymbolDefinitionNode identifierNode) {
        this.type = type;
        this.identifierNode = identifierNode;
    }

    public Symbol(Token.TokenType type, SymbolDefinitionNode identifierNode, Symbol scope) {
        this(type, identifierNode);
        this.scope = scope;
    }

    public Token.TokenType getType() { return type; }
    public SymbolDefinitionNode getNode() { return identifierNode; }
    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }
}
