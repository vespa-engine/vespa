package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventPositionContext extends EventDocumentContext {
    public final Position position;
    public final Position startOfWord;

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
        this.startOfWord = (document instanceof SchemaDocument) ? ((SchemaDocument)document).getPreviousStartOfWord(position) : position;
    }

    public Position startOfWord() {
        return this.startOfWord;
    }
}
