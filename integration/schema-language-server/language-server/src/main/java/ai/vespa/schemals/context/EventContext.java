package ai.vespa.schemals.context;

import java.io.PrintStream;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentParser;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventContext {

    public final PrintStream logger;
    public final SchemaDocumentScheduler scheduler;
    public final SchemaIndex schemaIndex;
    public final SchemaDocumentParser document;
    public final SchemaMessageHandler messageHandler;

    public EventContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler,
        String fileURI
    ) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.schemaIndex = schemaIndex;
        this.messageHandler = messageHandler;
        this.document = scheduler.getDocument(fileURI);
    }

}
