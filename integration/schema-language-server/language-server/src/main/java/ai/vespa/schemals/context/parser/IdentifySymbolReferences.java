package ai.vespa.schemals.context.parser;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.fieldsElm;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolDefinitionNode;
import ai.vespa.schemals.tree.SymbolReferenceNode;

public class IdentifySymbolReferences extends Identifier {

    public IdentifySymbolReferences(ParseContext context) {
		super(context);
	}

    private static final HashMap<Class<? extends Node>, TokenType> identifierTypeMap = new HashMap<Class<? extends Node>, TokenType>() {{
        put(inheritsDocument.class, TokenType.DOCUMENT);
        put(fieldsElm.class, TokenType.FIELD);
        put(rootSchema.class, TokenType.SCHEMA);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (node instanceof SymbolDefinitionNode) return ret;

        boolean isIdentifier = node.getClassLeafIdentifierString().equals("identifierStr");

        if (!isIdentifier) return ret;

        SchemaNode parent = node.getParent();
        if (parent == null) return ret;

        TokenType tokenType = identifierTypeMap.get(parent.getASTClass());
        if (tokenType == null) return ret;

        SymbolReferenceNode newNode = new SymbolReferenceNode(node, tokenType);

        // TODO: verify that the symbol is defined

        return ret;
    }
}
