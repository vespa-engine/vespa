// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.logserver.protocol;

import ai.vespa.logserver.protocol.protobuf.LogProtocol;
import com.google.protobuf.InvalidProtocolBufferException;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

/**
 * Utility class for serialization of log requests and responses.
 *
 * @author bjorncs
 */
class ProtobufSerialization {

    private ProtobufSerialization() {}

    static List<LogMessage> fromLogRequest(byte[] logRequestPayload) {
        try {
            LogProtocol.LogRequest logRequest = LogProtocol.LogRequest.parseFrom(logRequestPayload);
            return logRequest.getLogMessagesList().stream()
                .map(ProtobufSerialization::fromLogRequest)
                .toList();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse log request: " + e.getMessage(), e);
        }
    }

    static byte[] toLogRequest(List<LogMessage> logMessages) {
        LogProtocol.LogRequest.Builder builder = LogProtocol.LogRequest.newBuilder();
        for (LogMessage logMessage : logMessages) {
            builder.addLogMessages(toLogRequestMessage(logMessage));
        }
        return builder.build().toByteArray();
    }

    static byte[] toLogResponse() {
        return LogProtocol.LogResponse.newBuilder().build().toByteArray();
    }

    static void fromLogResponse(byte[] logResponsePayload) {
        try {
            LogProtocol.LogResponse.parseFrom(logResponsePayload); // log response is empty
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse log response: " + e.getMessage(), e);
        }
    }

    private static LogMessage fromLogRequest(LogProtocol.LogMessage message) {
        return LogMessage.of(
                Instant.ofEpochSecond(0, message.getTimeNanos()),
                message.getHostname(),
                message.getProcessId(),
                message.getThreadId(),
                message.getService(),
                message.getComponent(),
                fromLogMessageLevel(message.getLevel()),
                message.getPayload());
    }

    private static LogProtocol.LogMessage toLogRequestMessage(LogMessage logMessage) {
        Instant timestamp = logMessage.getTimestamp();
        long timestampNanos = timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano();
        return LogProtocol.LogMessage.newBuilder()
                .setTimeNanos(timestampNanos)
                .setHostname(logMessage.getHost())
                .setProcessId((int) logMessage.getProcessId())
                .setThreadId((int) logMessage.getThreadId().orElse(0))
                .setService(logMessage.getService())
                .setComponent(logMessage.getComponent())
                .setLevel(toLogMessageLevel(logMessage.getLevel()))
                .setPayload(logMessage.getPayload())
                .build();
    }

    @SuppressWarnings("deprecation")
    private static Level fromLogMessageLevel(LogProtocol.LogMessage.Level level) {
        switch (level) {
            case FATAL:
                return LogLevel.FATAL;
            case ERROR:
                return LogLevel.ERROR;
            case WARNING:
                return LogLevel.WARNING;
            case CONFIG:
                return LogLevel.CONFIG;
            case INFO:
                return LogLevel.INFO;
            case EVENT:
                return LogLevel.EVENT;
            case DEBUG:
                return LogLevel.DEBUG;
            case SPAM:
                return LogLevel.SPAM;
            case UNKNOWN:
            case UNRECOGNIZED:
            default:
                return LogLevel.UNKNOWN;
        }
    }

    @SuppressWarnings("deprecation")
    private static LogProtocol.LogMessage.Level toLogMessageLevel(Level level) {
        Level vespaLevel = LogLevel.getVespaLogLevel(level);
        if (vespaLevel.equals(LogLevel.FATAL)) {
            return LogProtocol.LogMessage.Level.FATAL;
        } else if (vespaLevel.equals(LogLevel.ERROR)) {
            return LogProtocol.LogMessage.Level.ERROR;
        } else if (vespaLevel.equals(LogLevel.WARNING)) {
            return LogProtocol.LogMessage.Level.WARNING;
        } else if (vespaLevel.equals(LogLevel.CONFIG)) {
            return LogProtocol.LogMessage.Level.CONFIG;
        } else if (vespaLevel.equals(LogLevel.INFO)) {
            return LogProtocol.LogMessage.Level.INFO;
        } else if (vespaLevel.equals(LogLevel.EVENT)) {
            return LogProtocol.LogMessage.Level.EVENT;
        } else if (vespaLevel.equals(LogLevel.DEBUG)) {
            return LogProtocol.LogMessage.Level.DEBUG;
        } else if (vespaLevel.equals(LogLevel.SPAM)) {
            return LogProtocol.LogMessage.Level.SPAM;
        } else {
            return LogProtocol.LogMessage.Level.UNKNOWN;
        }
    }

}
