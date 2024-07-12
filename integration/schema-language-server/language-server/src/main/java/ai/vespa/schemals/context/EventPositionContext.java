package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventPositionContext extends EventContext {
    public final Position position;

    public enum EnclosingBody {
        ROOT,
        SCHEMA,
        DOCUMENT,
        FIELD
    }


    public EventPositionContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler,
        String fileURI,
        Position position
    ) {
        super(logger, scheduler, schemaIndex, messageHandler, fileURI);
        this.position = position;
    }

    public Position startOfWord() {
        return document.getPreviousStartOfWord(position);
    }
}
