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

    private static final HashMap<Class<? extends Node>, TokenType> identifierTypeMap = new HashMap<Class<? extends Node>, TokenType>() {{
        put(rootSchema.class, TokenType.SCHEMA);
        put(documentElm.class, TokenType.DOCUMENT);
        put(namedDocument.class, TokenType.DOCUMENT);
        put(fieldElm.class, TokenType.FIELD);
        put(structFieldDefinition.class, TokenType.FIELD);
        put(fieldSetElm.class, TokenType.FIELDSET);
        put(structDefinitionElm.class, TokenType.STRUCT);
        put(structFieldElm.class, TokenType.STRUCT_FIELD);
        put(functionElm.class, TokenType.FUNCTION);
    }};

    private static final HashMap<Class<? extends Node>, TokenType> identifierWithDashTypeMap = new HashMap<Class<? extends Node>, TokenType>() {{
        put(rankProfile.class, TokenType.RANK_PROFILE);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        boolean isIdentifier = node.getClassLeafIdentifierString().equals("identifierStr");
        boolean isIdentifierWithDash = node.getClassLeafIdentifierString().equals("identifierWithDashStr");

        if (!isIdentifier && !isIdentifierWithDash) return ret;

        SchemaNode parent = node.getParent();
        if (parent == null || parent.size() <= 1) return ret;

        // Prevent inheritance from beeing marked as a definition
        if (parent.indexOf(node) >= 3) return ret;

        HashMap<Class<? extends Node>, TokenType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        TokenType tokenType = searchMap.get(parent.getASTClass());
        if (tokenType == null) return ret;

        SymbolDefinitionNode newNode = new SymbolDefinitionNode(node, tokenType);
        if (context.schemaIndex().findSymbolInFile(context.fileURI(), tokenType, node.getText()) == null) {
            Symbol symbol = new Symbol(newNode, context.fileURI());
            context.schemaIndex().insert(context.fileURI(), symbol);
        }

        return ret;
    }
}
