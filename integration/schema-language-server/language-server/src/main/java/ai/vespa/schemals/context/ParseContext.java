package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.List;
import java.util.ArrayList;

import ai.vespa.schemals.context.parser.*;
import ai.vespa.schemals.tree.TypeNode;

public class ParseContext { 
    private String content;
    private PrintStream logger;
    private String fileURI;
    private List<Identifier> identifiers;
    private List<TypeNode> unresolvedTypeNodes;
    private SchemaIndex schemaIndex;

    public ParseContext(String content, PrintStream logger, String fileURI, SchemaIndex schemaIndex) {
        this.content = content;
        this.logger = logger;
        this.fileURI = fileURI;
        this.schemaIndex = schemaIndex;
        this.unresolvedTypeNodes = new ArrayList<>();
        ParseContext self = this;
        this.identifiers = new ArrayList<>() {{
            add(new IdentifyType(self));
            add(new IdentifySymbolDefinition(self));
            add(new IdentifySymbolReferences(self));
            add(new IdentifyDeprecatedToken(self));
            add(new IdentifyDirtyNodes(self));
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

    public SchemaIndex schemaIndex() {
        return this.schemaIndex;
    }

    public void addIdentifier(Identifier identifier) {
        this.identifiers.add(identifier);
    }

    public void addUnresolvedTypeNode(TypeNode node) {
        this.unresolvedTypeNodes.add(node);
    }

    public List<TypeNode> unresolvedTypeNodes() {
        return this.unresolvedTypeNodes;
    }

    public void clearUnresolvedTypeNodes() {
        this.unresolvedTypeNodes.clear();
    }
}
