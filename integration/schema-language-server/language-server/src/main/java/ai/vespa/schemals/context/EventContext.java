package ai.vespa.schemals.context;

import java.io.PrintStream;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * EventContext
 */
public class EventContext {
    public final PrintStream logger;
    public final SchemaIndex schemaIndex;
    public final SchemaMessageHandler messageHandler;
    public final SchemaDocumentScheduler scheduler;

    public EventContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler
    ) {
		this.logger = logger;
		this.scheduler = scheduler;
		this.schemaIndex = schemaIndex;
		this.messageHandler = messageHandler;
    }
}
