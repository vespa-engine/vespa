package ai.vespa.schemals.context;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventPositionContext extends EventDocumentContext {
    public final Position position;
    public final Position startOfWord;

    public EventPositionContext(
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler,
        TextDocumentIdentifier documentIdentifier,
        Position position
    ) throws InvalidContextException {
        super(scheduler, schemaIndex, messageHandler, documentIdentifier);
        this.position = position;

        Position result = StringUtils.getPreviousStartOfWord(document.getCurrentContent(), position);
        this.startOfWord = (result != null ? result : position);
    }

    public Position startOfWord() {
        return this.startOfWord;
    }
}
