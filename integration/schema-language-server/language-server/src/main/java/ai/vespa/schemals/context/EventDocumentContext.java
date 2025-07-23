package ai.vespa.schemals.context;

import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventDocumentContext extends EventContext {
    public final DocumentManager document;
    public final TextDocumentIdentifier documentIdentifier;

    public EventDocumentContext(
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler,
        TextDocumentIdentifier documentIdentifier
    ) throws InvalidContextException {
        super(scheduler, schemaIndex, messageHandler);
        this.documentIdentifier = documentIdentifier;
        this.document = scheduler.getDocument(FileUtils.decodeURL(documentIdentifier.getUri()));
        if (this.document == null) {
            throw new InvalidContextException();
        }
    }

}
