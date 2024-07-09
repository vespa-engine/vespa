package ai.vespa.schemals.context.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.lsp4j.Diagnostic;

import com.yahoo.schema.Schema;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.namedDocument;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolDefinitionNode;

public class IdentifySymbolDefinition extends Identifier {

    public IdentifySymbolDefinition(ParseContext context) {
		super(context);
	}

    private static final HashMap<TokenType, HashSet<Class<? extends Node>>> tokenParentClassPairs = new HashMap<TokenType, HashSet<Class<? extends Node>>>() {{
        put(TokenType.SCHEMA, new HashSet<Class<? extends Node>>() {{
            add(rootSchema.class);
        }});
        put(TokenType.SEARCH, new HashSet<Class<? extends Node>>() {{
            add(rootSchema.class);
        }});
        put(TokenType.DOCUMENT, new HashSet<Class<? extends Node>>() {{
            add(documentElm.class);
            add(namedDocument.class);
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

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        SchemaNode parent = node.getParent();
        if (parent == null || parent.get(0) != node) return ret;

        TokenType nodeType = node.getType();
        HashSet<Class<? extends Node>> parentCNComp = tokenParentClassPairs.get(nodeType);
        if (
            parentCNComp == null ||
            !parentCNComp.contains(parent.getASTClass()) ||
            parent.size() <= 1
        ) {
            return ret;
        }
        
        SchemaNode child = parent.get(1);

        while (child.size() > 0) {
            child = child.get(0);
        }

        // TODO: move this check to the ccc file
        if (
            nodeType == TokenType.FIELD &&
            Schema.isReservedName(child.getText().toLowerCase())
        ) {
            ret.add(new Diagnostic(child.getRange(), "Reserved name '" + child.getText() + "' can not be used as a field name."));
        
        } else {

            SymbolDefinitionNode newNode = new SymbolDefinitionNode(child);
            if (context.schemaIndex().findSymbol(context.fileURI(), nodeType, child.getText()) == null) {
                Symbol symbol = new Symbol(nodeType, newNode);
                context.schemaIndex().insert(context.fileURI(), symbol);
            }

        }

        return ret;
    }
}
