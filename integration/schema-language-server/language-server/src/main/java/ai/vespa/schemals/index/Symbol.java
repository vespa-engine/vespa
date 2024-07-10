package ai.vespa.schemals.index;

import ai.vespa.schemals.tree.SchemaNode;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.SchemaDocumentParser;

public class Symbol {
    private SchemaNode identifierNode;
    private Symbol scope = null;
    private String fileURI;
    private SymbolType type;
    private SymbolStatus status;

    public Symbol(SchemaNode identifierNode, SymbolType type, String fileURI) {
        this.identifierNode = identifierNode;
        this.fileURI = fileURI;
        this.type = type;
        this.status = SymbolStatus.UNRESOLVED;
    }

    public Symbol(SchemaNode identifierNode, SymbolType type, String fileURI, Symbol scope) {
        this(identifierNode, type, fileURI);
        this.scope = scope;
    }

    public String getFileURI() { return fileURI; }
    
    public String setFileURI(String fileURI) {
        this.fileURI = fileURI;
        return fileURI;
    }
    
    public void setType(SymbolType type) { this.type = type; }
    public SymbolType getType() { return type; }
    public void setStatus(SymbolStatus status) { this.status = status; }
    public SymbolStatus getStatus() { return status; }

    public SchemaNode getNode() { return identifierNode; }

    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Symbol other = (Symbol) obj;
        return (
            this.fileURI.equals(other.fileURI) &&
            this.type == other.type &&
            this.getNode() != null &&
            other.getNode() != null &&
            this.getNode().getRange() != null &&
            other.getNode().getRange() != null &&
            this.getNode().getRange().equals(other.getNode().getRange())
        );
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.fileURI == null) ? 0 : this.fileURI.hashCode());
        result = prime * result + ((this.type == null) ? 0 : this.type.hashCode());
        result = prime * result + ((this.getNode() == null || this.getNode().getRange() == null) ? 0 : this.getNode().getRange().hashCode());
        return result;
    }

    public enum SymbolStatus {
        DEFINITION,
        REFERENCE,
        UNRESOLVED,
        INVALID
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

    public String toString() {
        Position pos = getNode().getRange().getStart();
        String fileName = SchemaDocumentParser.fileNameFromPath(fileURI);
        return "Symbol('" + getShortIdentifier() + "', at: " + fileName + ":" + pos.getLine() + ":" + pos.getCharacter() + ")";
    }
}
