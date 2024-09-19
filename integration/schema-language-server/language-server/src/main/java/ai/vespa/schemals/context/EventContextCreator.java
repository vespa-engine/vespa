package ai.vespa.schemals.context;

import java.io.PrintStream;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class EventContextCreator {
    public final SchemaDocumentScheduler scheduler;
    public final SchemaIndex schemaIndex;
    public final SchemaMessageHandler messageHandler;

    public EventContextCreator(
        SchemaDocumentScheduler scheduler,
        SchemaIndex schemaIndex,
        SchemaMessageHandler messageHandler
    ) {
        this.scheduler = scheduler;
        this.schemaIndex = schemaIndex;
        this.messageHandler = messageHandler;
    }

    public EventPositionContext createContext(TextDocumentPositionParams params) throws InvalidContextException {
        return new EventPositionContext(
            scheduler,
            schemaIndex,
            messageHandler,
            params.getTextDocument(),
            params.getPosition()
        );
    }

    public EventCompletionContext createContext(CompletionParams params) throws InvalidContextException {
        return new EventCompletionContext(
            scheduler, 
            schemaIndex, 
            messageHandler, 
            params.getTextDocument(), 
            params.getPosition(), 
            params.getContext().getTriggerCharacter());
    }

    public EventDocumentContext createContext(SemanticTokensParams params) throws InvalidContextException {
        return new EventDocumentContext(scheduler, schemaIndex, messageHandler, params.getTextDocument());
    }

    public EventDocumentContext createContext(DocumentSymbolParams params) throws InvalidContextException {
        return new EventDocumentContext(scheduler, schemaIndex, messageHandler, params.getTextDocument());
    }

    public EventCodeActionContext createContext(CodeActionParams params) throws InvalidContextException {
        if (params.getContext() == null) return null;
        if (params.getRange() == null) return null;
        if (params.getContext().getDiagnostics() == null) return null;
        if (params.getTextDocument().getUri() == null) return null;

        return new EventCodeActionContext(
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
        return new EventExecuteCommandContext(scheduler, schemaIndex, messageHandler, params);
    }

    public EventDocumentContext createContext(CodeLensParams params) throws InvalidContextException {
        return new EventDocumentContext(scheduler, schemaIndex, messageHandler, params.getTextDocument());
    }

}
