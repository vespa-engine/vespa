package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

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

    public EventContext createContext(TextDocumentPositionParams params) {
        return createContext(params.getTextDocument().getUri());
    }

    public EventContext createContext(SemanticTokensParams params) {
        return createContext(params.getTextDocument().getUri());
    }

    public EventContext createContext(String fileURI) {
        SchemaDocumentParser document = scheduler.getDocument(fileURI);
        return new EventContext(logger, scheduler, schemaIndex, document);
    }
}