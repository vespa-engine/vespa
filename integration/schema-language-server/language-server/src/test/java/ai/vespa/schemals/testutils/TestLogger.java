package ai.vespa.schemals.testutils;

import org.eclipse.lsp4j.MessageType;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.SchemaMessageHandler;

/**
 * TestLogger
 */
public class TestLogger extends ClientLogger {
    private TestSchemaMessageHandler testMessageHandler;

    public TestLogger(TestSchemaMessageHandler messageHandler) {
        super(messageHandler);
        testMessageHandler = messageHandler;
    }

    public TestLogger() {
        this(new TestSchemaMessageHandler());
    }

    public void info(Object message) {
        if (!messageHandler.connected())
            return;
        messageHandler.logMessage(MessageType.Info, message.toString());
    }

    public void error(Object message) {
        if (messageHandler.connected()) {
            messageHandler.logMessage(MessageType.Error, message.toString());
        }

        throw new RuntimeException("A error was logged to the client, this should never happen. Error message:" + message.toString());
    }

    public void warning(Object message) {
        if (!messageHandler.connected())
            return;
        messageHandler.logMessage(MessageType.Warning, message.toString());
    }

    public String getLog() {
        return testMessageHandler.getLog();
    }
}
