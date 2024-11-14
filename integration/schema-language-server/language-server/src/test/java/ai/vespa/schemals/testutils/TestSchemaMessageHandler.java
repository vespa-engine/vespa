package ai.vespa.schemals.testutils;

import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentResult;

import ai.vespa.schemals.SchemaMessageHandler;

/**
 * TestSchemaMessageHandler
 */
public class TestSchemaMessageHandler extends SchemaMessageHandler {

    private StringBuilder log;

    public TestSchemaMessageHandler() {
        super();
        log = new StringBuilder();
    }

    @Override
    public boolean connected() { return true; }

    @Override
    public void sendMessage(MessageType messageType, String message) { }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(String message, List<MessageActionItem> actions) { return null; }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(String fileURI) { return null; }

    @Override
    public void verboseTrace(String logMessage) { }

    @Override
    public void messageTrace(String logMessage) { }

    @Override
    public void logMessage(MessageType messageType, String message) {
        log.append(message + "\n");
    }

    public String getLog() {
        return log.toString();
    }
}
