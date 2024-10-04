package ai.vespa.schemals.index;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.tree.SchemaNode;

import java.net.URI;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

/**
 * Symbol represents a symbol in a document.
 */
public class Symbol {
    private SchemaNode identifierNode;
    private Symbol scope = null;
    private String fileURI;
    private URI fileURIimpl; // for comparing with other symbols file URIs
    private SymbolType type;
    private SymbolStatus status;
    private String overrideShortIdentifier = null;

    public Symbol(SchemaNode identifierNode, SymbolType type, String fileURI, Symbol scope, String shortIdentifier) {
        this.identifierNode = identifierNode;
        this.setFileURI(fileURI);
        this.type = type;
        this.status = SymbolStatus.UNRESOLVED;
        this.scope = scope;
        this.overrideShortIdentifier = shortIdentifier;
    }

    public Symbol(SchemaNode identifierNode, SymbolType type, String fileURI) {
        this(identifierNode, type, fileURI, null, null);
    }

    public Symbol(SchemaNode identifierNode, SymbolType type, String fileURI, Symbol scope) {
        this(identifierNode, type, fileURI, scope, null);
    }

    public String getFileURI() { return fileURI; }
    
    public String setFileURI(String fileURI) {
        this.fileURI = fileURI;
        this.fileURIimpl = URI.create(fileURI);
        return fileURI;
    }

    public boolean fileURIEquals(Symbol other) {
        return fileURIimpl.equals(other.fileURIimpl);
    }

    public boolean fileURIEquals(String otherURI) {
        return fileURIimpl.equals(URI.create(otherURI));
    }

    public boolean fileURIEquals(URI otherURI) {
        return fileURIimpl.equals(otherURI);
    }
    
    public void setType(SymbolType type) { this.type = type; }
    public SymbolType getType() { return type; }
    public void setStatus(SymbolStatus status) { this.status = status; }
    public SymbolStatus getStatus() { return status; }

    public Symbol getScope() { return scope; }
    // Be careful when using this
    public void setScope(Symbol scope) { this.scope = scope; }

    // TODO: not quite sure if this kind of equality check is good
    public boolean isInScope(Symbol scope) {
        if (scope == null || this.scope == null) return false;
        return this.scope.equals(scope);
    }

    public SchemaNode getNode() { return identifierNode; }

    public String getShortIdentifier() {
        if (overrideShortIdentifier != null) return overrideShortIdentifier;

        return identifierNode.getText();
    }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
    }

    /*
     * Same as long identifier, but without SCHEMA and DOCUMENT
     * Mainly for rendering purposes in LSP
     */
    public String getPrettyIdentifier() {
        if (type == SymbolType.DOCUMENT || type == SymbolType.SCHEMA) return getShortIdentifier();
        if (scope == null 
                || scope.getType() == SymbolType.DOCUMENT 
                || scope.getType() == SymbolType.SCHEMA) return getShortIdentifier();
        return scope.getPrettyIdentifier() + "." + getShortIdentifier();
    }

    public Location getLocation() {
        return new Location(fileURI, identifierNode.getRange());
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
            this.fileURIEquals(other) &&
            this.type == other.type &&
            this.status == other.status &&
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
        result = prime * result + ((this.fileURI == null) ? 0 : fileURIimpl.hashCode()); // use URI hashCode, not String hashCode because URI might be "same but different"
        result = prime * result + ((this.type == null) ? 0 : this.type.hashCode());
        result = prime * result + ((this.getNode() == null || this.getNode().getRange() == null) ? 0 : this.getNode().getRange().hashCode());
        return result;
    }

    public enum SymbolStatus {
        DEFINITION,
        REFERENCE,
        UNRESOLVED,
        INVALID,
        BUILTIN_REFERENCE // reference to stuff like "default" that doesn't have a definition in our CSTs
    }

    public enum SymbolType {
        ANNOTATION,
        DOCUMENT,
        DOCUMENT_SUMMARY,
        FIELD,
        FIELDSET,
        FUNCTION,
        LABEL,
        LAMBDA_FUNCTION,
        MAP_KEY,
        MAP_VALUE,
        ONNX_MODEL,
        PARAMETER,
        PROPERTY,
        QUERY_INPUT,
        RANK_CONSTANT,
        RANK_PROFILE,
        RANK_TYPE,
        SCHEMA,
        STRUCT,
        STRUCT_FIELD,
        SUBFIELD,
        TENSOR,
        TENSOR_DIMENSION_INDEXED,
        TENSOR_DIMENSION_MAPPED,
        TYPE_UNKNOWN,
        DIMENSION,
    }

    public String toString() {
        Position pos = getNode().getRange().getStart();
        String fileName = FileUtils.fileNameFromPath(fileURI);
        return "Symbol('" + getLongIdentifier() + "', " + getType() + ", " + getStatus() + ", at: " + fileName + ":" + pos.getLine() + ":" + pos.getCharacter() + ")@" + System.identityHashCode(this);
    }
}
