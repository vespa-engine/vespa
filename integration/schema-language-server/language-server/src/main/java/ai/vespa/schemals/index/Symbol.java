package ai.vespa.schemals.index;

import ai.vespa.schemals.tree.SchemaNode;

public class Symbol {
    private SchemaNode identifierNode;
    private Symbol scope = null;
    private String fileURI;
    private SymbolType type;

    public Symbol(SchemaNode identifierNode, SymbolType type, String fileURI) {
        this.identifierNode = identifierNode;
        this.fileURI = fileURI;
        this.type = type;
    }

    public Symbol(SchemaNode identifierNode, SymbolType type, String fileURI, Symbol scope) {
        this(identifierNode, type, fileURI);
        this.scope = scope;
    }

    public void setType(SymbolType type) { this.type = type; }
    public SymbolType getType() { return type; }
    public String getFileURI() { return fileURI; }
    public SchemaNode getNode() { return identifierNode; }
    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }

    public enum SymbolType {
        SCHEMA,
        DOCUMENT,
        FIELD,
        STRUCT,
        ANNOTATION,
        RANK_PROFILE,
        FIELDSET,
        STRUCT_FIELD,
        FUNCTION,
        TYPE_UNKNOWN
    }
}
