// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.yolean.trace.TraceNode;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Formatting of an {@link AccessLogEntry} in the Vespa JSON access log format.
 *
 * @author frodelu
 */
public class JSONFormatter implements LogWriter<RequestLogEntry> {
    private static final String COVERAGE = "coverage";
    private static final String COVERAGE_COVERAGE = "coverage";
    private static final String COVERAGE_DOCUMENTS = "documents";
    private static final String COVERAGE_DEGRADE = "degraded";
    private static final String COVERAGE_DEGRADE_MATCHPHASE = "match-phase";
    private static final String COVERAGE_DEGRADE_TIMEOUT = "timeout";
    private static final String COVERAGE_DEGRADE_ADAPTIVE_TIMEOUT = "adaptive-timeout";
    private static final String COVERAGE_DEGRADED_NON_IDEAL_STATE = "non-ideal-state";

    private final JsonFactory generatorFactory;

    private static Logger logger = Logger.getLogger(JSONFormatter.class.getName());

    public JSONFormatter() {
        generatorFactory = new JsonFactory(new ObjectMapper());
    }

    @Override
    public void write(RequestLogEntry entry, OutputStream outputStream) throws IOException {
        try (JsonGenerator generator = createJsonGenerator(outputStream)){
            generator.writeStartObject();
            String peerAddress = entry.peerAddress().get();
            generator.writeStringField("ip", peerAddress);
            long time = entry.timestamp().get().toEpochMilli();
            FormatUtil.writeSecondsField(generator, "time", time);
            FormatUtil.writeSecondsField(generator, "duration", entry.duration().get());
            generator.writeNumberField("responsesize", entry.contentSize().orElse(0));
            generator.writeNumberField("code", entry.statusCode().orElse(0));
            generator.writeStringField("method", entry.httpMethod().orElse(""));
            generator.writeStringField("uri", getNormalizedURI(entry.rawPath().orElse(null), entry.rawQuery().orElse(null)));
            generator.writeStringField("version", entry.httpVersion().orElse(""));
            generator.writeStringField("agent", entry.userAgent().orElse(""));
            generator.writeStringField("host", entry.hostString().orElse(""));
            generator.writeStringField("scheme", entry.scheme().orElse(null));
            generator.writeNumberField("localport", entry.localPort().getAsInt());

            String connectionId = entry.connectionId().orElse(null);
            if (connectionId != null) {
                generator.writeStringField("connection", connectionId);
            }

            Principal userPrincipal = entry.userPrincipal().orElse(null);
            if (userPrincipal != null) {
                generator.writeStringField("user-principal", userPrincipal.getName());
            }

            Principal sslPrincipal = entry.sslPrincipal().orElse(null);
            if (sslPrincipal != null) {
                generator.writeStringField("ssl-principal", sslPrincipal.getName());
            }

            String remoteAddress = entry.remoteAddress().orElse(null);
            int remotePort = entry.remotePort().orElse(0);
            // Only add remote address/port fields if relevant
            if (remoteAddressDiffers(peerAddress, remoteAddress)) {
                generator.writeStringField("remoteaddr", remoteAddress);
                if (remotePort > 0) {
                    generator.writeNumberField("remoteport", remotePort);
                }
            }

            // Only add peer address/port fields if relevant
            if (peerAddress != null) {
                generator.writeStringField("peeraddr", peerAddress);

                int peerPort = entry.peerPort().getAsInt();
                if (peerPort > 0 && peerPort != remotePort) {
                    generator.writeNumberField("peerport", peerPort);
                }
            }

            TraceNode trace = entry.traceNode().orElse(null);
            if (trace != null) {
                long timestamp = trace.timestamp();
                if (timestamp == 0L) {
                    timestamp = time;
                }
                trace.accept(new TraceRenderer(generator, timestamp));
            }

            // Only add search sub block of this is a search request
            if (isSearchRequest(entry)) {
                HitCounts hitCounts = entry.hitCounts().get();
                generator.writeObjectFieldStart("search");
                generator.writeNumberField("totalhits", getTotalHitCount(hitCounts));
                generator.writeNumberField("hits", getRetrievedHitCount(hitCounts));
                Coverage c = hitCounts.getCoverage();
                if (c != null) {
                    generator.writeObjectFieldStart(COVERAGE);
                    generator.writeNumberField(COVERAGE_COVERAGE, c.getResultPercentage());
                    generator.writeNumberField(COVERAGE_DOCUMENTS, c.getDocs());
                    if (c.isDegraded()) {
                        generator.writeObjectFieldStart(COVERAGE_DEGRADE);
                        if (c.isDegradedByMatchPhase())
                            generator.writeBooleanField(COVERAGE_DEGRADE_MATCHPHASE, c.isDegradedByMatchPhase());
                        if (c.isDegradedByTimeout())
                            generator.writeBooleanField(COVERAGE_DEGRADE_TIMEOUT, c.isDegradedByTimeout());
                        if (c.isDegradedByAdapativeTimeout())
                            generator.writeBooleanField(COVERAGE_DEGRADE_ADAPTIVE_TIMEOUT, c.isDegradedByAdapativeTimeout());
                        if (c.isDegradedByNonIdealState())
                            generator.writeBooleanField(COVERAGE_DEGRADED_NON_IDEAL_STATE, c.isDegradedByNonIdealState());
                        generator.writeEndObject();
                    }
                    generator.writeEndObject();
                }
                generator.writeEndObject();
            }

            // Add key/value access log entries. Keys with single values are written as single
            // string value fields while keys with multiple values are written as string arrays
            Collection<String> keys = entry.extraAttributeKeys();
            if (!keys.isEmpty()) {
                generator.writeObjectFieldStart("attributes");
                for (String key : keys) {
                    Collection<String> values = entry.extraAttributeValues(key);
                    if (values.size() == 1) {
                        generator.writeStringField(key, values.iterator().next());
                    } else {
                        generator.writeFieldName(key);
                        generator.writeStartArray();
                        for (String s : values) {
                            generator.writeString(s);
                        }
                        generator.writeEndArray();
                    }
                }
                generator.writeEndObject();
            }

            generator.writeEndObject();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to generate JSON access log entry: " + e.getMessage(), e);
        }
    }

    private JsonGenerator createJsonGenerator(OutputStream outputStream) throws IOException {
        return generatorFactory.createGenerator(outputStream, JsonEncoding.UTF8)
                .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                .configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false);
    }

    private boolean remoteAddressDiffers(String ipV4Address, String remoteAddress) {
        return remoteAddress != null && !Objects.equals(ipV4Address, remoteAddress);
    }

    private boolean isSearchRequest(RequestLogEntry entry) {
        return entry != null && entry.hitCounts().isPresent();
    }

    private long getTotalHitCount(HitCounts counts) {
        if (counts == null) {
            return 0;
        }

        return counts.getTotalHitCount();
    }

    private int getRetrievedHitCount(HitCounts counts) {
        if (counts == null) {
            return 0;
        }

        return counts.getRetrievedHitCount();
    }

    private static String getNormalizedURI(String rawPath, String rawQuery) {
        if (rawPath == null) return null;
        return rawQuery != null ? rawPath + "?" + rawQuery : rawPath;
    }
}
