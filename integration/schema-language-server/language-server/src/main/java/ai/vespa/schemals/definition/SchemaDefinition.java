package ai.vespa.schemals.definition;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Location;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolNode;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.ast.fieldsElm;

public class SchemaDefinition {

    private static HashMap<Class<? extends Node>, Token.TokenType> linkContexts = new HashMap<Class<? extends Node>, Token.TokenType>() {{
        put(fieldsElm.class, Token.TokenType.FIELD);
    }};

    public static ArrayList<Location> getDefinition(
        EventPositionContext context
    ) {

        ArrayList<Location> ret = new ArrayList<Location>();

        SchemaDocumentParser document = context.document;

        SchemaNode node = document.getLeafNodeAtPosition(context.position);

        if (node == null || !(node instanceof SymbolNode)) {
            return ret;
        }

        SchemaNode parent = node.getParent();
        if (parent == null) {
            return ret;
        }

        Token.TokenType tokenType = linkContexts.get(parent.getASTClass());
        if (tokenType == null) {
            return ret;
        }

        Symbol refersTo = context.schemaIndex.findSymbolInFile(document.getFileURI(), tokenType, node.getText());
        
        if (refersTo == null) {
            return ret;
        }

        ret.add(new Location(document.getFileURI(), refersTo.getNode().getRange()));
        return ret;
    }
}
