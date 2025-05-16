package ai.vespa.schemals.testutils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.MessageType;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.SchemaMessageHandler;

/**
 * TestLogger
 */
public class TestLogger extends ClientLogger {
    private TestSchemaMessageHandler testMessageHandler;
    private List<String> errors;

    public TestLogger(TestSchemaMessageHandler messageHandler) {
        super(messageHandler);
        testMessageHandler = messageHandler;
        errors = new ArrayList<>();
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

        errors.add(message.toString());
    }

    public void warning(Object message) {
        if (!messageHandler.connected())
            return;
        messageHandler.logMessage(MessageType.Warning, message.toString());
    }

    public String getLog() {
        return testMessageHandler.getLog();
    }

    public List<String> getErrorMessages() {
        return errors;
    }
}
