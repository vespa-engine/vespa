package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventContextCreator {
    public final PrintStream logger;
    public final SchemaDocumentScheduler scheduler;
    public final SchemaIndex schemaIndex;
    public final SchemaMessageHandler messageHandler;

    public EventContextCreator(
        PrintStream logger,
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler
    ) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.schemaIndex = schemaIndex;
        this.messageHandler = messageHandler;
    }

    public EventPositionContext createContext(TextDocumentPositionParams params) {
        return new EventPositionContext(
            logger,
            scheduler,
            schemaIndex,
            messageHandler,
            params.getTextDocument(),
            params.getPosition()
        );
    }

    public EventCompletionContext createContext(CompletionParams params) {
        return new EventCompletionContext(
            logger, 
            scheduler, 
            schemaIndex, 
            messageHandler, 
            params.getTextDocument(), 
            params.getPosition(), 
            params.getContext().getTriggerCharacter());
    }

    public EventDocumentContext createContext(SemanticTokensParams params) {
        return new EventDocumentContext(logger, scheduler, schemaIndex, messageHandler, params.getTextDocument());
    }

    public EventDocumentContext createContext(DocumentSymbolParams params) {
        return new EventDocumentContext(logger, scheduler, schemaIndex, messageHandler, params.getTextDocument());
    }

    public EventCodeActionContext createContext(CodeActionParams params) {
        if (params.getContext() == null) return null;
        if (params.getRange() == null) return null;
        if (params.getContext().getDiagnostics() == null) return null;
        if (params.getTextDocument().getUri() == null) return null;

        return new EventCodeActionContext(
            logger, 
            scheduler, 
            schemaIndex, 
            messageHandler, 
            params.getTextDocument(), 
            params.getRange(), 
            params.getContext().getDiagnostics(),
            params.getContext().getOnly()
        );
    }

    public EventExecuteCommandContext createContext(ExecuteCommandParams params) {
        return new EventExecuteCommandContext(logger, scheduler, schemaIndex, messageHandler, params);
    }

}
