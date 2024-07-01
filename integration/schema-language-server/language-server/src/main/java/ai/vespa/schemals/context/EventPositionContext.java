package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;

public class EventPositionContext extends EventContext {
    public final Position position;

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

}
