package ai.vespa.schemals.common;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.eclipse.lsp4j.MessageType;

import ai.vespa.schemals.SchemaMessageHandler;

/**
 * ClientLogger
 * Responsible for logging only.
 */
public class ClientLogger {
    protected SchemaMessageHandler messageHandler;

    public ClientLogger(SchemaMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void info(Object message) {
        if (!messageHandler.connected())
            return;
        messageHandler.logMessage(MessageType.Info, message.toString());
    }

    public static String errorToString(Exception ex) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream logger = new PrintStream(outputStream);
        ex.printStackTrace(logger);

        return outputStream.toString();
    }

    public void error(Exception ex) {
        error(errorToString(ex));
    }

    public void error(Object message) {
        if (!messageHandler.connected())
            return;
        messageHandler.logMessage(MessageType.Error, message.toString());
    }

    public void warning(Object message) {
        if (!messageHandler.connected())
            return;
        messageHandler.logMessage(MessageType.Warning, message.toString());
    }
}
