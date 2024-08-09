package ai.vespa.schemals.context;

import org.eclipse.lsp4j.ExecuteCommandParams;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * EventExecuteCommandContext
 */
public class EventExecuteCommandContext extends EventContext {

    public final ExecuteCommandParams params;

	public EventExecuteCommandContext(SchemaDocumentScheduler scheduler, SchemaIndex schemaIndex,
			SchemaMessageHandler messageHandler, ExecuteCommandParams params) {
		super(scheduler, schemaIndex, messageHandler);
        this.params = params;
	}
}
