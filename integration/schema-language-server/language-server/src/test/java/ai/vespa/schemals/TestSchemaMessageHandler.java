package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentResult;

/**
 * TestSchemaMessageHandler
 */
public class TestSchemaMessageHandler extends SchemaMessageHandler {
    public TestSchemaMessageHandler(PrintStream logger) {
        super(logger);
    }

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
    public void logMessage(MessageType messageType, String message) { }
}
