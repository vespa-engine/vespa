package ai.vespa.schemals.hover;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaHover {
    

    static public Hover getHover(SchemaDocumentParser document, Position position) {

        SchemaNode node = document.getLeafNodeAtPosition(position);

        if (node == null) {
            return null;
        }

        return new Hover(new MarkupContent("plaintext", node.getIdentifierString()), node.getRange());
    }
}
