package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.ExecuteCommandParams;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * EventExecuteCommandContext
 */
public class EventExecuteCommandContext extends EventContext {

    public final ExecuteCommandParams params;

	public EventExecuteCommandContext(PrintStream logger, SchemaDocumentScheduler scheduler, SchemaIndex schemaIndex,
			SchemaMessageHandler messageHandler, ExecuteCommandParams params) {
		super(logger, scheduler, schemaIndex, messageHandler);
        this.params = params;
	}
}
