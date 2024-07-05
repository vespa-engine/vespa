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
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolDefinitionNode;

public class IdentifySymbolDefinition extends Identifier {

    protected String fileURI;
    protected SchemaIndex schemaIndex;

    private static final HashMap<TokenType, HashSet<Class<? extends Node>>> tokenParentClassPairs = new HashMap<TokenType, HashSet<Class<? extends Node>>>() {{
        put(TokenType.SCHEMA, new HashSet<Class<? extends Node>>() {{
            add(rootSchema.class);
        }});
        put(TokenType.DOCUMENT, new HashSet<Class<? extends Node>>() {{
            add(documentElm.class);
        }});
        put(TokenType.FIELD, new HashSet<Class<? extends Node>>() {{
            add(fieldElm.class);
            add(structFieldDefinition.class);
        }});
        put(TokenType.FIELDSET, new HashSet<Class<? extends Node>>() {{
            add(fieldSetElm.class);
        }});
        put(TokenType.STRUCT, new HashSet<Class<? extends Node>>() {{
            add(structDefinitionElm.class);
        }});
        put(TokenType.STRUCT_FIELD, new HashSet<Class<? extends Node>>() {{
            add(structFieldElm.class);
        }});
        put(TokenType.RANK_PROFILE, new HashSet<Class<? extends Node>>() {{
            add(rankProfile.class);
        }});
        put(TokenType.FUNCTION, new HashSet<Class<? extends Node>>() {{
            add(functionElm.class);
        }});
    }};

    public IdentifySymbolDefinition(PrintStream logger, String fileURI, SchemaIndex schemaIndex) {
        super(logger);
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        SchemaNode parent = node.getParent();
        TokenType nodeType = node.getType();
        HashSet<Class<? extends Node>> parentCNComp = tokenParentClassPairs.get(nodeType);
        if (
            parent != null &&
            parent.get(0) == node &&
            parentCNComp != null &&
            parentCNComp.contains(parent.getIdentifierClass()) &&
            parent.size() > 1
        ) {
            SchemaNode child = parent.get(1);

            // TODO: move this check to the ccc file
            if (
                nodeType == TokenType.FIELD &&
                Schema.isReservedName(child.getText().toLowerCase())
            ) {
                ret.add(new Diagnostic(child.getRange(), "Reserved name '" + child.getText() + "' can not be used as a field name."));
            
            } else {

                SymbolDefinitionNode newNode = new SymbolDefinitionNode(child);
                if (schemaIndex.findSymbol(fileURI, nodeType, child.getText()) == null) {
                    Symbol symbol = new Symbol(nodeType, newNode);
                    schemaIndex.insert(fileURI, symbol);
                }

            }
        }

        return ret;
    }
}
