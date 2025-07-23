package ai.vespa.schemals.context;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * EventCompletionContext
 */
public class EventCompletionContext extends EventPositionContext {
    public final Character triggerCharacter;

	public EventCompletionContext(
            SchemaDocumentScheduler scheduler, 
            SchemaIndex schemaIndex,
			SchemaMessageHandler messageHandler, 
            TextDocumentIdentifier documentIdentifier, 
            Position position, 
            String triggerCharacter) throws InvalidContextException {
		super(scheduler, schemaIndex, messageHandler, documentIdentifier, position);

        if (triggerCharacter == null || triggerCharacter.length() == 0) {
            this.triggerCharacter = Character.valueOf('\0');
        } else {
            this.triggerCharacter = triggerCharacter.charAt(0);
        }
	}
}
