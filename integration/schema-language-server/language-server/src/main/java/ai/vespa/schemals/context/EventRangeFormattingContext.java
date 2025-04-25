package ai.vespa.schemals.context;

import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventRangeFormattingContext extends EventFormattingContext {

    private Range formattingRange;

    public EventRangeFormattingContext(SchemaDocumentScheduler scheduler, SchemaIndex schemaIndex,
            SchemaMessageHandler messageHandler, TextDocumentIdentifier documentIdentifier,
            FormattingOptions formattingOptions, Range range) throws InvalidContextException {
        super(scheduler, schemaIndex, messageHandler, documentIdentifier, formattingOptions);
        this.formattingRange = range;
    }

    public Range getRange() { return formattingRange; }
}
