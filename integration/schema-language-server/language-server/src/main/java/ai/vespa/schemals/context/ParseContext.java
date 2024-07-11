package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.List;
import java.util.ArrayList;

import ai.vespa.schemals.context.parser.*;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.tree.SchemaNode;

import ai.vespa.schemals.index.SchemaIndex;

public class ParseContext { 
    private String content;
    private PrintStream logger;
    private String fileURI;
    private List<Identifier> identifiers;
    private List<SchemaNode> unresolvedInheritanceNodes;
    private List<SchemaNode> unresolvedTypeNodes;
    private List<SchemaNode> unresolvedAnnotationReferenceNodes;
    private SchemaIndex schemaIndex;
    private SchemaNode inheritsSchemaNode;

    public ParseContext(String content, PrintStream logger, String fileURI, SchemaIndex schemaIndex) {
        this.content = content;
        this.logger = logger;
        this.fileURI = fileURI;
        this.schemaIndex = schemaIndex;
        this.unresolvedInheritanceNodes = new ArrayList<>();
        this.unresolvedTypeNodes = new ArrayList<>();
        this.unresolvedAnnotationReferenceNodes = new ArrayList<>();
        ParseContext context = this;
        this.inheritsSchemaNode = null;
        this.identifiers = new ArrayList<>() {{
            add(new IdentifyType(context));

            add(new IdentifySymbolDefinition(context));
            add(new IdentifySymbolReferences(context));
            add(new IdentifyAnnotationReference(context));

            add(new IdentifySchemaInheritance(context));
            add(new IdentifyDocumentInheritance(context));
            add(new IdentifyStructInheritance(context));
            add(new IdentifyRankProfileInheritance(context));

            add(new IdentifyDeprecatedToken(context));
            add(new IdentifyDirtyNodes(context));
            add(new IdentifyDocumentlessSchema(context));
            add(new IdentifyNamedDocument(context));
        }};
    }

    public String content() {
        return this.content;
    }

    public PrintStream logger() {
        return this.logger;
    }

    public String fileURI() {
        return this.fileURI;
    }

    public List<Identifier> identifiers() {
        return this.identifiers;
    }

    public List<SchemaNode> unresolvedInheritanceNodes() {
        return this.unresolvedInheritanceNodes;
    }

    public List<SchemaNode> unresolvedTypeNodes() {
        return this.unresolvedTypeNodes;
    }

    public List<SchemaNode> unresolvedAnnotationReferenceNodes() {
        return this.unresolvedAnnotationReferenceNodes;
    }

    public SchemaIndex schemaIndex() {
        return this.schemaIndex;
    }

    public void addIdentifier(Identifier identifier) {
        this.identifiers.add(identifier);
    }

    public void addUnresolvedTypeNode(SchemaNode node) {
        this.unresolvedTypeNodes.add(node);
    }

    public void addUnresolvedAnnotationReferenceNode(SchemaNode node) {
        this.unresolvedAnnotationReferenceNodes.add(node);
    }

    public void addUnresolvedInheritanceNode(SchemaNode nameNode) {
        this.unresolvedInheritanceNodes.add(nameNode);
    }

    public void clearUnresolvedTypeNodes() {
        this.unresolvedTypeNodes.clear();
    }

    public void clearUnresolvedAnnotationReferenceNodes() {
        this.unresolvedAnnotationReferenceNodes.clear();
    }

    public void clearUnresolvedInheritanceNodes() {
        this.unresolvedInheritanceNodes.clear();
    }

    public SchemaNode inheritsSchemaNode() {
        return this.inheritsSchemaNode;
    }

    public void setInheritsSchemaNode(SchemaNode schemaNode) {
        this.inheritsSchemaNode = schemaNode;
    }
}
