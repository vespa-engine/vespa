package ai.vespa.schemals.index;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SymbolDefinitionNode;

public class Symbol {
    private SymbolDefinitionNode identifierNode;
    private Symbol scope = null;
    private String fileURI;

    public Symbol(SymbolDefinitionNode identifierNode, String fileURI) {
        this.identifierNode = identifierNode;
        this.fileURI = fileURI;
    }

    public Symbol(SymbolDefinitionNode identifierNode, String fileURI, Symbol scope) {
        this(identifierNode, fileURI);
        this.scope = scope;
    }

    public TokenType getType() { return identifierNode.getSymbolType(); }
    public String getFileURI() { return fileURI; }
    public SymbolDefinitionNode getNode() { return identifierNode; }
    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }
}
