package ai.vespa.schemals.definition;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.tree.SchemaNode;

import ai.vespa.schemals.parser.*;

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

        SchemaNode refersTo = context.schemaIndex.findSymbol(document.getFileURI(), tokenType, node.getText());
        
        if (refersTo == null) {
            return ret;
        }

        ret.add(new Location(document.getFileURI(), refersTo.getRange()));
        return ret;
    }
}
