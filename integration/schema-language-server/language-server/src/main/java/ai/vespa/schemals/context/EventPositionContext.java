package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.tree.SchemaNode;

public class EventPositionContext extends EventContext {
    public final Position position;

    public enum EnclosingType {
        SCHEMA,
        DOCUMENT,
        FIELD
    }

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

    public EnclosingType findEnclosingType() {
        SchemaNode node = document.getNodeAtPosition(position);
        while (node != null) {
            this.logger.println(node.getIdentifierString());
            node = node.getParent();
        }
        return EnclosingType.SCHEMA;
    }
}
