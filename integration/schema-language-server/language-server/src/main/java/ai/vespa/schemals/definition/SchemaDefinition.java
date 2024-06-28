package ai.vespa.schemals.definition;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.tree.SchemaNode;

import ai.vespa.schemals.parser.*;

public class SchemaDefinition {

    public static ArrayList<Location> getDefinition(
        EventPositionContext context
    ) {

        SchemaDocumentParser document = context.document;

        SchemaNode node = document.getLeafNodeAtPosition(context.position);

        SchemaNode refersTo = context.schemaIndex.findSymbol(document.getFileURI(), Token.TokenType.FIELD, node.getText());
        
        if (refersTo == null) {
            return new ArrayList<Location>();
        }

        return new ArrayList<Location>() {{
            add(new Location(document.getFileURI(), refersTo.getRange()));
        }};
    }
}
