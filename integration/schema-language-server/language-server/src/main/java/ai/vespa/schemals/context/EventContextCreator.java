package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import ai.vespa.schemals.index.SchemaIndex;

public class EventContextCreator {
    public final PrintStream logger;
    public final SchemaDocumentScheduler scheduler;
    public final SchemaIndex schemaIndex;

    public EventContextCreator(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex
    ) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.schemaIndex = schemaIndex;
    }

    public EventPositionContext createContext(TextDocumentPositionParams params) {
        return new EventPositionContext(
            logger,
            scheduler,
            schemaIndex,
            params.getTextDocument().getUri(),
            params.getPosition()
        );
    }

    public EventContext createContext(SemanticTokensParams params) {
        return new EventContext(logger, scheduler, schemaIndex, params.getTextDocument().getUri());
    }

}
