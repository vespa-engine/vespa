package ai.vespa.schemals.definition;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Location;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.Symbol;
import ai.vespa.schemals.tree.SchemaNode;

import ai.vespa.schemals.parser.Token;

public class SchemaDefinition {

    private static HashMap<String, Token.TokenType> linkContexts = new HashMap<String, Token.TokenType>() {{
        put("ai.vespa.schemals.parser.ast.fieldsElm", Token.TokenType.FIELD);
    }};

    public static ArrayList<Location> getDefinition(
        EventPositionContext context
    ) {

        ArrayList<Location> ret = new ArrayList<Location>();

        SchemaDocumentParser document = context.document;

        SchemaNode node = document.getLeafNodeAtPosition(context.position);

        if (node == null || !node.isUserDefinedIdentifier()) {
            return ret;
        }

        SchemaNode parent = node.getParent();
        if (parent == null) {
            return ret;
        }

        Token.TokenType tokenType = linkContexts.get(parent.getIdentifierString());
        if (tokenType == null) {
            return ret;
        }

        Symbol refersTo = context.schemaIndex.findSymbol(document.getFileURI(), tokenType, node.getText());
        
        if (refersTo == null) {
            return ret;
        }

        ret.add(new Location(document.getFileURI(), refersTo.getNode().getRange()));
        return ret;
    }
}
