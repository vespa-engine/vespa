package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.List;
import java.util.ArrayList;

import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.index.FieldIndex;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.schemadocument.parser.IdentifyType;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument.UnresolvedFieldArgument;
import ai.vespa.schemals.schemadocument.parser.IdentifySymbolDefinition;
import ai.vespa.schemals.schemadocument.parser.IdentifySymbolReferences;
import ai.vespa.schemals.schemadocument.parser.IdentifySchemaInheritance;
import ai.vespa.schemals.schemadocument.parser.IdentifyDocumentInheritance;
import ai.vespa.schemals.schemadocument.parser.IdentifyDocumentSummaryInheritance;
import ai.vespa.schemals.schemadocument.parser.IdentifyStructInheritance;
import ai.vespa.schemals.schemadocument.parser.IdentifyRankProfileInheritance;
import ai.vespa.schemals.schemadocument.parser.IdentifyDeprecatedToken;
import ai.vespa.schemals.schemadocument.parser.IdentifyDirtyNodes;
import ai.vespa.schemals.schemadocument.parser.IdentifyDocumentlessSchema;
import ai.vespa.schemals.schemadocument.parser.IdentifyNamedDocument;

public class ParseContext { 
    private String content;
    private PrintStream logger;
    private String fileURI;
    private List<Identifier> identifiers;
    private List<SchemaNode> unresolvedInheritanceNodes;
    private List<SchemaNode> unresolvedTypeNodes;
    private List<SchemaNode> unresolvedDocumentReferenceNodes;
    private List<UnresolvedFieldArgument> unresolvedFieldArgumentNodes;
    private SchemaIndex schemaIndex;
    private SchemaNode inheritsSchemaNode;
    private SchemaDocumentScheduler scheduler;

    public ParseContext(String content, PrintStream logger, String fileURI, SchemaIndex schemaIndex, SchemaDocumentScheduler scheduler) {
        this.content = content;
        this.logger = logger;
        this.fileURI = fileURI;
        this.schemaIndex = schemaIndex;
        this.unresolvedInheritanceNodes = new ArrayList<>();
        this.unresolvedTypeNodes = new ArrayList<>();
        this.unresolvedDocumentReferenceNodes = new ArrayList<>();
        this.unresolvedFieldArgumentNodes = new ArrayList<>();
        this.inheritsSchemaNode = null;
        this.identifiers = new ArrayList<>();
        this.scheduler = scheduler;
    }

    /*
     * Identifiers used when parsing a .sd file
     */
    public void useDocumentIdentifiers() {
        ParseContext context = this;
        this.identifiers = new ArrayList<>() {{
            add(new IdentifyType(context));

            add(new IdentifySymbolDefinition(context));
            add(new IdentifySymbolReferences(context));

            add(new IdentifySchemaInheritance(context));
            add(new IdentifyDocumentInheritance(context));
            add(new IdentifyStructInheritance(context));
            add(new IdentifyRankProfileInheritance(context));
            add(new IdentifyDocumentSummaryInheritance(context));

            add(new IdentifyDeprecatedToken(context));
            add(new IdentifyDirtyNodes(context));
            add(new IdentifyDocumentlessSchema(context));
            add(new IdentifyNamedDocument(context));
        }};
    }

    /*
     * Identifiers used when parsing a .profile file
     */
    public void useRankProfileIdentifiers() {
        ParseContext context = this;

        this.identifiers = new ArrayList<>() {{
            add(new IdentifySymbolDefinition(context));
            add(new IdentifySymbolReferences(context));
            add(new IdentifyRankProfileInheritance(context));
            add(new IdentifyDirtyNodes(context));
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

    public List<SchemaNode> unresolvedDocumentReferenceNodes() {
        return this.unresolvedDocumentReferenceNodes;
    }

    public List<UnresolvedFieldArgument> unresolvedFieldArguments() {
        return this.unresolvedFieldArgumentNodes;
    }

    public SchemaIndex schemaIndex() {
        return this.schemaIndex;
    }

    // Convenience
    public FieldIndex fieldIndex() {
        return this.schemaIndex.fieldIndex();
    }

    public SchemaDocumentScheduler scheduler() {
        return this.scheduler;
    }

    public void addIdentifier(Identifier identifier) {
        this.identifiers.add(identifier);
    }

    public void addUnresolvedTypeNode(SchemaNode node) {
        this.unresolvedTypeNodes.add(node);
    }

    public void addUnresolvedInheritanceNode(SchemaNode nameNode) {
        this.unresolvedInheritanceNodes.add(nameNode);
    }

    public void addUnresolvedDocumentReferenceNode(SchemaNode node) {
        this.unresolvedDocumentReferenceNodes.add(node);
    }

    public void addUnresolvedFieldArgument(UnresolvedFieldArgument node) {
        this.unresolvedFieldArgumentNodes.add(node);
    }

    public void clearUnresolvedTypeNodes() {
        this.unresolvedTypeNodes.clear();
    }

    public void clearUnresolvedInheritanceNodes() {
        this.unresolvedInheritanceNodes.clear();
    }

    public void clearUnresolvedDocumentReferenceNodes() {
        this.unresolvedDocumentReferenceNodes.clear();
    }

    public void clearUnresolvedFieldArguments() {
        this.unresolvedFieldArgumentNodes.clear();
    }

    public SchemaNode inheritsSchemaNode() {
        return this.inheritsSchemaNode;
    }

    public void setInheritsSchemaNode(SchemaNode schemaNode) {
        this.inheritsSchemaNode = schemaNode;
    }
}
