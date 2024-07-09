package ai.vespa.schemals.index;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SymbolNode;

public class Symbol {
    private SymbolNode identifierNode;
    private Symbol scope = null;
    private String fileURI;

    public Symbol(SymbolNode identifierNode, String fileURI) {
        this.identifierNode = identifierNode;
        this.fileURI = fileURI;
    }

    public Symbol(SymbolNode identifierNode, String fileURI, Symbol scope) {
        this(identifierNode, fileURI);
        this.scope = scope;
    }

    public TokenType getType() { return identifierNode.getSymbolType(); }
    public String getFileURI() { return fileURI; }
    public SymbolNode getNode() { return identifierNode; }
    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }

    public String toString() {
        return getShortIdentifier();
    }
}
