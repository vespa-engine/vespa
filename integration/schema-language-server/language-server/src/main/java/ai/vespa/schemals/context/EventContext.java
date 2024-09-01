package ai.vespa.schemals.context;

import java.io.PrintStream;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * EventContext
 */
public class EventContext {
    public final ClientLogger logger;
    public final SchemaIndex schemaIndex;
    public final SchemaMessageHandler messageHandler;
    public final SchemaDocumentScheduler scheduler;

    public EventContext(
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler
    ) {
		this.scheduler = scheduler;
		this.schemaIndex = schemaIndex;
		this.messageHandler = messageHandler;
		this.logger = new ClientLogger(messageHandler);
    }
}
