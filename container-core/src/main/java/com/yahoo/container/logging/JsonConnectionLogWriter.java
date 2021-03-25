// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.logging.ConnectionLogEntry.SslHandshakeFailure.ExceptionEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * @author bjorncs
 */
class JsonConnectionLogWriter implements LogWriter<ConnectionLogEntry> {

    private final JsonFactory jsonFactory = new JsonFactory(new ObjectMapper());

    @Override
    public void write(ConnectionLogEntry record, OutputStream outputStream) throws IOException {
        try (JsonGenerator generator = createJsonGenerator(outputStream)) {
            generator.writeStartObject();
            generator.writeStringField("id", record.id());
            generator.writeStringField("timestamp", record.timestamp().toString());

            writeOptionalSeconds(generator, "duration", unwrap(record.durationSeconds()));
            writeOptionalString(generator, "peerAddress", unwrap(record.peerAddress()));
            writeOptionalInteger(generator, "peerPort", unwrap(record.peerPort()));
            writeOptionalString(generator, "localAddress", unwrap(record.localAddress()));
            writeOptionalInteger(generator, "localPort", unwrap(record.localPort()));
            writeOptionalString(generator, "remoteAddress", unwrap(record.remoteAddress()));
            writeOptionalInteger(generator, "remotePort", unwrap(record.remotePort()));
            writeOptionalLong(generator, "httpBytesReceived", unwrap(record.httpBytesReceived()));
            writeOptionalLong(generator, "httpBytesSent", unwrap(record.httpBytesSent()));
            writeOptionalLong(generator, "requests", unwrap(record.requests()));
            writeOptionalLong(generator, "responses", unwrap(record.responses()));

            String sslProtocol = unwrap(record.sslProtocol());
            String sslSessionId = unwrap(record.sslSessionId());
            String sslCipherSuite = unwrap(record.sslCipherSuite());
            String sslPeerSubject = unwrap(record.sslPeerSubject());
            Instant sslPeerNotBefore = unwrap(record.sslPeerNotBefore());
            Instant sslPeerNotAfter = unwrap(record.sslPeerNotAfter());
            String sslSniServerName = unwrap(record.sslSniServerName());
            ConnectionLogEntry.SslHandshakeFailure sslHandshakeFailure = unwrap(record.sslHandshakeFailure());

            if (isAnyValuePresent(
                    sslProtocol, sslSessionId, sslCipherSuite, sslPeerSubject, sslPeerNotBefore, sslPeerNotAfter,
                    sslSniServerName, sslHandshakeFailure)) {
                generator.writeObjectFieldStart("ssl");

                writeOptionalString(generator, "protocol", sslProtocol);
                writeOptionalString(generator, "sessionId", sslSessionId);
                writeOptionalString(generator, "cipherSuite", sslCipherSuite);
                writeOptionalString(generator, "peerSubject", sslPeerSubject);
                writeOptionalTimestamp(generator, "peerNotBefore", sslPeerNotBefore);
                writeOptionalTimestamp(generator, "peerNotAfter", sslPeerNotAfter);
                writeOptionalString(generator, "sniServerName", sslSniServerName);

                if (sslHandshakeFailure != null) {
                    generator.writeObjectFieldStart("handshake-failure");
                    generator.writeArrayFieldStart("exception");
                    for (ExceptionEntry entry : sslHandshakeFailure.exceptionChain()) {
                        generator.writeStartObject();
                        generator.writeStringField("cause", entry.name());
                        generator.writeStringField("message", entry.message());
                        generator.writeEndObject();
                    }
                    generator.writeEndArray();
                    generator.writeStringField("type", sslHandshakeFailure.type());
                    generator.writeEndObject();
                }

                generator.writeEndObject();
            }
        }
    }

    private void writeOptionalString(JsonGenerator generator, String name, String value) throws IOException {
        if (value != null) {
            generator.writeStringField(name, value);
        }
    }

    private void writeOptionalInteger(JsonGenerator generator, String name, Integer value) throws IOException {
        if (value != null) {
            generator.writeNumberField(name, value);
        }
    }

    private void writeOptionalLong(JsonGenerator generator, String name, Long value) throws IOException {
        if (value != null) {
            generator.writeNumberField(name, value);
        }
    }

    private void writeOptionalTimestamp(JsonGenerator generator, String name, Instant value) throws IOException {
        if (value != null) {
            generator.writeStringField(name, value.toString());
        }
    }

    private void writeOptionalSeconds(JsonGenerator generator, String name, Double value) throws IOException {
        if (value != null) {
            FormatUtil.writeSecondsField(generator, name, value);
        }
    }

    private static boolean isAnyValuePresent(Object... values) { return Arrays.stream(values).anyMatch(Objects::nonNull); }
    private static <T> T unwrap(Optional<T> maybeValue) { return maybeValue.orElse(null); }

    private JsonGenerator createJsonGenerator(OutputStream outputStream) throws IOException {
        return jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8)
                .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                .configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false);
    }
}
