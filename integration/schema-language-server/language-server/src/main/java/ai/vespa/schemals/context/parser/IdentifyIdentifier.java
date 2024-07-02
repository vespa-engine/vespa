package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.context.Symbol;
import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyIdentifier extends Identifier {

    protected SchemaDocumentParser document;
    protected SchemaIndex schemaIndex;

    private static final HashMap<Token.TokenType, HashSet<String>> tokenParentClassPairs = new HashMap<Token.TokenType, HashSet<String>>() {{
        put(Token.TokenType.SCHEMA, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.rootSchema");
        }});
        put(Token.TokenType.DOCUMENT, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.documentElm");
        }});
        put(Token.TokenType.FIELD, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.fieldElm");
            add("ai.vespa.schemals.parser.ast.structFieldDefinition");
        }});
        put(Token.TokenType.FIELDSET, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.fieldSetElm");
        }});
        put(Token.TokenType.STRUCT, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.structDefinitionElm");
        }});
        put(Token.TokenType.RANK_PROFILE, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.rankProfile");
        }});
    }};

    public IdentifyIdentifier(PrintStream logger, SchemaDocumentParser document, SchemaIndex schemaIndex) {
        super(logger);
        this.document = document;
        this.schemaIndex = schemaIndex;
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        SchemaNode parent = node.getParent();
        Token.TokenType nodeType = node.getType();
        HashSet<String> parentCNComp = tokenParentClassPairs.get(nodeType);
        if (
            parent != null &&
            parent.get(0) == node &&
            parentCNComp != null &&
            parentCNComp.contains(parent.getIdentifierString()) &&
            parent.size() > 1
        ) {
            SchemaNode child = parent.get(1);
            child.setUserDefinedIdentifier();
            if (schemaIndex.findSymbol(document.getFileURI(), nodeType, child.getText()) == null) {
                Symbol symbol = new Symbol(nodeType, child);
                schemaIndex.insert(document.getFileURI(), symbol);
            } else {
                ret.add(new Diagnostic(child.getRange(), "Duplicate identifier"));
            }

        }

        return ret;
    }
}
