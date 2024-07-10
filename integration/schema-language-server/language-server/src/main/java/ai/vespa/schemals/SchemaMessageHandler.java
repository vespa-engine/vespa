package ai.vespa.schemals;

import java.io.PrintStream;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;

public class SchemaMessageHandler {

    // TODO: implement logging


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
}
