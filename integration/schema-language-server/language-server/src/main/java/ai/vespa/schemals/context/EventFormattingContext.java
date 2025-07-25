package ai.vespa.schemals.context;

import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;
import ai.vespa.schemals.schemadocument.DocumentManager.DocumentType;

public class EventFormattingContext extends EventDocumentContext {

    private FormattingOptions formattingOptions;

    public EventFormattingContext(SchemaDocumentScheduler scheduler, SchemaIndex schemaIndex,
            SchemaMessageHandler messageHandler, TextDocumentIdentifier documentIdentifier, FormattingOptions formattingOptions)
            throws InvalidContextException {
        super(scheduler, schemaIndex, messageHandler, documentIdentifier);

        if (this.document.getDocumentType() == DocumentType.YQL) {
            throw new InvalidContextException("Formatting not supported for yql (yet).");
        }

        this.formattingOptions = formattingOptions;
    }

    public FormattingOptions getOptions() { return this.formattingOptions; }
}
