package ai.vespa.schemals.index;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SymbolDefinitionNode;

public class Symbol {
    private SymbolDefinitionNode identifierNode;
    private Symbol scope = null;

    public Symbol(SymbolDefinitionNode identifierNode) {
        this.identifierNode = identifierNode;
    }

    public Symbol(SymbolDefinitionNode identifierNode, Symbol scope) {
        this(identifierNode);
        this.scope = scope;
    }

    public TokenType getType() { return identifierNode.getSymbolType(); }
    public SymbolDefinitionNode getNode() { return identifierNode; }
    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }
}
