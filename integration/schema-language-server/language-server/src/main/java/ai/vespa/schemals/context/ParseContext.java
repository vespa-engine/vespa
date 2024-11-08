package ai.vespa.schemals.context;

import java.util.List;
import java.util.ArrayList;

import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.index.FieldIndex;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.schemadocument.parser.IdentifyDirtyNodes;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyDeprecatedToken;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyDirtySchemaNodes;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyDocumentInheritance;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyDocumentSummaryInheritance;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyDocumentlessSchema;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyNamedDocument;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyRankProfileInheritance;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifySchemaInheritance;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyStructInheritance;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifySymbolDefinition;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifySymbolReferences;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifyType;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument.UnresolvedFieldArgument;

public class ParseContext { 
    private String content;
    private String fileURI;
    private ClientLogger logger;
    private List<Identifier<SchemaNode>> identifiers;
    private List<Identifier<YQLNode>> YQLIdentifiers;
    private List<SchemaNode> unresolvedInheritanceNodes;
    private List<SchemaNode> unresolvedTypeNodes;
    private List<SchemaNode> unresolvedDocumentReferenceNodes;
    private List<UnresolvedFieldArgument> unresolvedFieldArgumentNodes;
    private SchemaIndex schemaIndex;
    private SchemaNode inheritsSchemaNode;
    private SchemaDocumentScheduler scheduler;

    public ParseContext(String content, ClientLogger logger, String fileURI, SchemaIndex schemaIndex, SchemaDocumentScheduler scheduler) {
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
        this.YQLIdentifiers = new ArrayList<>();
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
            add(new IdentifyDirtySchemaNodes(context));
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
            add(new IdentifyDirtySchemaNodes(context));
        }};
    }

    public void useVespaGroupingIdentifiers() {
        ParseContext context = this;

        this.YQLIdentifiers = new ArrayList<>() {{
            add(new IdentifyDirtyNodes<YQLNode>(context));
        }};
    }

    public String content() {
        return this.content;
    }

    public ClientLogger logger() {
        return this.logger;
    }

    public String fileURI() {
        return this.fileURI;
    }

    public List<Identifier<SchemaNode>> identifiers() {
        return this.identifiers;
    }

    public List<Identifier<YQLNode>> YQLIdentifiers() {
        return this.YQLIdentifiers;
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

    public void addIdentifier(Identifier<SchemaNode> identifier) {
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
