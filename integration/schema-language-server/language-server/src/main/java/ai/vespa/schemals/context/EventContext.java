package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.schemadocument.DocumentManager;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventContext {

    public final PrintStream logger;
    public final SchemaDocumentScheduler scheduler;
    public final SchemaIndex schemaIndex;
    public final DocumentManager document;
    public final SchemaMessageHandler messageHandler;
    public final TextDocumentIdentifier documentIdentifier;

    public EventContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler,
        TextDocumentIdentifier documentIdentifier
    ) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.schemaIndex = schemaIndex;
        this.messageHandler = messageHandler;
        this.documentIdentifier = documentIdentifier;
        this.document = scheduler.getDocument(documentIdentifier.getUri());
    }

}
