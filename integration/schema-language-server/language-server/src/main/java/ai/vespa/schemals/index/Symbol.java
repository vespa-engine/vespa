package ai.vespa.schemals.index;

import ai.vespa.schemals.tree.SchemaNode;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.parser.Token.TokenType;

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

    public void setType(SymbolType type) { this.type = type; }
    public SymbolType getType() { return type; }
    public void setStatus(SymbolStatus status) { this.status = status; }
    public SymbolStatus getStatus() { return status; }
    public String getFileURI() { return fileURI; }

    // TODO: not quite sure if this kind of equality check is good
    public boolean isInScope(Symbol scope) {
        if (scope == null || this.scope == null) return false;
        return this.scope == scope;
    }

    public SchemaNode getNode() { return identifierNode; }

    public String getShortIdentifier() { return identifierNode.getText(); }

    public String getLongIdentifier() {
        if (scope == null) {
            return getShortIdentifier();
        }
        return scope.getLongIdentifier() + "." + getShortIdentifier();
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
        TYPE_UNKNOWN,
        FIELD_IN_STRUCT
    }
    public String toString() {
        Position pos = getNode().getRange().getStart();
        String fileName = SchemaDocumentParser.fileNameFromPath(fileURI);
        return "Symbol('" + getShortIdentifier() + "', at: " + fileName + ":" + pos.getLine() + ":" + pos.getCharacter() + ")";
    }
}
