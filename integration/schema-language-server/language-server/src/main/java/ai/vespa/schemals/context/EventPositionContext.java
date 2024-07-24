package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventPositionContext extends EventContext {
    public final Position position;

    public EventPositionContext(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler,
        TextDocumentIdentifier documentIdentifier,
        Position position
    ) {
        super(logger, scheduler, schemaIndex, messageHandler, documentIdentifier);
        this.position = position;
    }

    public Position startOfWord() {
        if (!(document instanceof SchemaDocument)) return this.position;
        return ((SchemaDocument)document).getPreviousStartOfWord(position);
    }
}
