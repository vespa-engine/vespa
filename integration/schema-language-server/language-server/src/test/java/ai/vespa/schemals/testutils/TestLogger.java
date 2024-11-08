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
        if (!messageHandler.connected()) return;
        messageHandler.logMessage(MessageType.Info, message.toString());
    }

    public void error(Object message) {
        if (!messageHandler.connected()) return;
        messageHandler.logMessage(MessageType.Error, message.toString());
    }

    public void warning(Object message) {
        if (!messageHandler.connected()) return;
        messageHandler.logMessage(MessageType.Warning, message.toString());
    }

    public String getLog() {
        return testMessageHandler.getLog();
    }
}
