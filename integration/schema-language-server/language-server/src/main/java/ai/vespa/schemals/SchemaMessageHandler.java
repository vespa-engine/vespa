package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;


public class SchemaMessageHandler {
    private PrintStream logger;
    private LanguageClient client;

    SchemaMessageHandler(PrintStream logger) {
        this.logger = logger;
    }

    void connectClient(LanguageClient client) {
        this.client = client;
    }

    public void sendMessage(MessageType messageType, String message) {
        client.showMessage(new MessageParams(messageType, message));
    }

    public CompletableFuture<MessageActionItem> showMessageRequest(String message, List<MessageActionItem> actions) {
        ShowMessageRequestParams params = new ShowMessageRequestParams(actions);
        params.setMessage(message);
        params.setType(MessageType.Info);
        return client.showMessageRequest(params);
    }
}
