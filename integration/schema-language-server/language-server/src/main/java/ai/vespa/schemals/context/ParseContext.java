package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.List;
import java.util.ArrayList;

import ai.vespa.schemals.context.parser.*;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.tree.TypeNode;
import ai.vespa.schemals.tree.SchemaNode;

public class ParseContext { 
    private String content;
    private PrintStream logger;
    private String fileURI;
    private List<Identifier> identifiers;
    private List<SchemaNode> unresolvedInheritanceNodes;
    private List<TypeNode> unresolvedTypeNodes;
    private SchemaIndex schemaIndex;

    public ParseContext(String content, PrintStream logger, String fileURI, SchemaIndex schemaIndex) {
        this.content = content;
        this.logger = logger;
        this.fileURI = fileURI;
        this.schemaIndex = schemaIndex;
        this.unresolvedInheritanceNodes = new ArrayList<>();
        this.unresolvedTypeNodes = new ArrayList<>();
        ParseContext context = this;
        this.identifiers = new ArrayList<>() {{
            add(new IdentifyType(context));
            add(new IdentifyDocumentInheritance(context));
            add(new IdentifySymbolDefinition(context));
            add(new IdentifySymbolReferences(context));
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

    public List<TypeNode> unresolvedTypeNodes() {
        return this.unresolvedTypeNodes;
    }

    public SchemaIndex schemaIndex() {
        return this.schemaIndex;
    }

    public void addIdentifier(Identifier identifier) {
        this.identifiers.add(identifier);
    }

    public void addUnresolvedTypeNode(TypeNode node) {
        this.unresolvedTypeNodes.add(node);
    }

    public void addUnresolvedInheritanceNode(SchemaNode nameNode) {
        this.unresolvedInheritanceNodes.add(nameNode);
    }

    public void clearUnresolvedTypeNodes() {
        this.unresolvedTypeNodes.clear();
    }

    public void clearUnresolvedInheritanceNodes() {
        this.unresolvedInheritanceNodes.clear();
    }
}
