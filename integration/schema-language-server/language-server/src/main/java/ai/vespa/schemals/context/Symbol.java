package ai.vespa.schemals.context;

import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.tree.SchemaNode;

public class Symbol {


    private Token.TokenType type;
    private SchemaNode identifierNode;
    private Symbol scope = null;

    public Symbol(Token.TokenType type, SchemaNode identifierNode) {
        this.type = type;
        this.identifierNode = identifierNode;
    }

    public Symbol(Token.TokenType type, SchemaNode identifierNode, Symbol scope) {
        this(type, identifierNode);
        this.scope = scope;
    }

    public Token.TokenType getType() { return type; }
    public SchemaNode getNode() { return identifierNode; }
    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }
}
