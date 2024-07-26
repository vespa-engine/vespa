package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.schemadocument.DocumentManager;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventDocumentContext extends EventContext {
    public final DocumentManager document;
    public final TextDocumentIdentifier documentIdentifier;

    public EventDocumentContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler,
        TextDocumentIdentifier documentIdentifier
    ) {
        super(logger, scheduler, schemaIndex, messageHandler);
        this.documentIdentifier = documentIdentifier;
        this.document = scheduler.getDocument(documentIdentifier.getUri());
    }

}
