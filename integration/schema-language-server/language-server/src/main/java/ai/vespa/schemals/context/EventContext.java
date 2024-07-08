package ai.vespa.schemals.context;

import java.io.PrintStream;

import ai.vespa.schemals.index.SchemaIndex;

public class EventContext {

    public final PrintStream logger;
    public final SchemaDocumentScheduler scheduler;
    public final SchemaIndex schemaIndex;
    public final SchemaDocumentParser document;

    public EventContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        String fileURI
    ) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.schemaIndex = schemaIndex;
        this.document = scheduler.getDocument(fileURI);
    }

}
