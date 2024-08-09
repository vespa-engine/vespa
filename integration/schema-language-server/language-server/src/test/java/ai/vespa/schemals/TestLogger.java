package ai.vespa.schemals;

import org.eclipse.lsp4j.MessageType;

import ai.vespa.schemals.common.ClientLogger;

/**
 * TestLogger
 */
public class TestLogger extends ClientLogger {

	public TestLogger(SchemaMessageHandler messageHandler) {
		super(messageHandler);
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
}
