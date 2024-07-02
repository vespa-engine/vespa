package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.HashMap;

import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.tree.SchemaNode;

public class EventPositionContext extends EventContext {
    public final Position position;

    public enum EnclosingBody {
        ROOT,
        SCHEMA,
        DOCUMENT,
        FIELD
    }

    // TODO: I want this in a type checkable way
    private static HashMap<String, EnclosingBody> enclosingBodyIdentifier = new HashMap<>() {{
        put("fieldElm", EnclosingBody.FIELD);
        put("documentElm", EnclosingBody.DOCUMENT);
        put("rootSchema", EnclosingBody.SCHEMA);
    }};

    public EventPositionContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        String fileURI,
        Position position
    ) {
        super(logger, scheduler, schemaIndex, fileURI);
        this.position = position;
    }

    public Position startOfWord() {
        return document.getPreviousStartOfWord(position);
    }

    public EnclosingBody findEnclosingBody() {
        SchemaNode node = document.getNodeAtPosition(startOfWord());

        if (node == null) {
            /* TODO: For now assume we are inside a schema body
             * This happens when trying to write something in a schema, because 
             * the fault tolerant parser closes the body before our current position
             */
            return EnclosingBody.SCHEMA;
        }

        while (node != null) {
            String identifier = node.getClassLeafIdentifierString();

            this.logger.println(identifier);

            EnclosingBody body = enclosingBodyIdentifier.get(identifier);
            if (body != null)return body;

            node = node.getParent();
        }
        return EnclosingBody.ROOT;
    }
}
