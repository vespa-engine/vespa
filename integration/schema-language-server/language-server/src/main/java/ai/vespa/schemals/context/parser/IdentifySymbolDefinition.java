package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.lsp4j.Diagnostic;

import com.yahoo.schema.Schema;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.context.Symbol;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolDefinitionNode;

public class IdentifySymbolDefinition extends Identifier {

    protected SchemaDocumentParser document;
    protected SchemaIndex schemaIndex;

    private static final HashMap<TokenType, HashSet<String>> tokenParentClassPairs = new HashMap<TokenType, HashSet<String>>() {{
        put(TokenType.SCHEMA, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.rootSchema");
        }});
        put(TokenType.DOCUMENT, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.documentElm");
        }});
        put(TokenType.FIELD, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.fieldElm");
            add("ai.vespa.schemals.parser.ast.structFieldDefinition");
        }});
        put(TokenType.FIELDSET, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.fieldSetElm");
        }});
        put(TokenType.STRUCT, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.structDefinitionElm");
        }});
        put(TokenType.STRUCT_FIELD, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.structFieldElm");
        }});
        put(TokenType.RANK_PROFILE, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.rankProfile");
        }});
        put(TokenType.FUNCTION, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.functionElm");
        }});
    }};

    public IdentifySymbolDefinition(PrintStream logger, SchemaDocumentParser document, SchemaIndex schemaIndex) {
        super(logger);
        this.document = document;
        this.schemaIndex = schemaIndex;
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        SchemaNode parent = node.getParent();
        TokenType nodeType = node.getType();
        HashSet<String> parentCNComp = tokenParentClassPairs.get(nodeType);
        if (
            parent != null &&
            parent.get(0) == node &&
            parentCNComp != null &&
            parentCNComp.contains(parent.getIdentifierString()) &&
            parent.size() > 1
        ) {
            SchemaNode child = parent.get(1);

            if (
                nodeType == TokenType.FIELD &&
                Schema.isReservedName(child.getText().toLowerCase())
            ) {
                ret.add(new Diagnostic(child.getRange(), "Reserved name '" + child.getText() + "' can not be used as a field name."));
            
            } else {

                SymbolDefinitionNode newNode = new SymbolDefinitionNode(child);
                if (schemaIndex.findSymbol(document.getFileURI(), nodeType, child.getText()) == null) {
                    Symbol symbol = new Symbol(nodeType, newNode);
                    schemaIndex.insert(document.getFileURI(), symbol);
                }

            }
        }

        return ret;
    }
}
