package ai.vespa.schemals;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TraceValue;
import org.eclipse.lsp4j.services.LanguageClient;


public class SchemaMessageHandler {
    private LanguageClient client;
    private String traceValue = TraceValue.Off;

    void connectClient(LanguageClient client) {
        this.client = client;
    }

    public boolean connected() {
        return client != null;
    }

    public void sendMessage(MessageType messageType, String message) {
        client.showMessage(new MessageParams(messageType, message));
    }

    public void logMessage(MessageType messageType, String message) {
        client.logMessage(new MessageParams(messageType, message));
    }

    public void setTraceValue(String newTraceValue) {
        this.traceValue = newTraceValue;
    }

    public void verboseTrace(String logMessage) {
        if (!traceValue.equals(TraceValue.Verbose)) return;
        client.logTrace(new LogTraceParams(logMessage));
    }

    public void messageTrace(String logMessage) {
        if (traceValue.equals(TraceValue.Off)) return;
        client.logTrace(new LogTraceParams(logMessage));
    }

    public CompletableFuture<MessageActionItem> showMessageRequest(String message, List<MessageActionItem> actions) {
        ShowMessageRequestParams params = new ShowMessageRequestParams(actions);
        params.setMessage(message);
        params.setType(MessageType.Info);
        return client.showMessageRequest(params);
    }

    public CompletableFuture<ShowDocumentResult> showDocument(String fileURI) {
        return client.showDocument(new ShowDocumentParams(fileURI));
    }
}
