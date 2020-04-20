// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.yolean.trace.TraceNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Formatting of an {@link AccessLogEntry} in the Vespa JSON access log format.
 *
 * @author frodelu
 */
public class JSONFormatter {
    private static final String COVERAGE = "coverage";
    private static final String COVERAGE_COVERAGE = "coverage";
    private static final String COVERAGE_DOCUMENTS = "documents";
    private static final String COVERAGE_DEGRADE = "degraded";
    private static final String COVERAGE_DEGRADE_MATCHPHASE = "match-phase";
    private static final String COVERAGE_DEGRADE_TIMEOUT = "timeout";
    private static final String COVERAGE_DEGRADE_ADAPTIVE_TIMEOUT = "adaptive-timeout";
    private static final String COVERAGE_DEGRADED_NON_IDEAL_STATE = "non-ideal-state";

    private AccessLogEntry accessLogEntry;
    private final JsonFactory generatorFactory;

    private static Logger logger = Logger.getLogger(JSONFormatter.class.getName());

    public JSONFormatter(final AccessLogEntry entry) {
        accessLogEntry = entry;
        generatorFactory = new JsonFactory();
        generatorFactory.setCodec(new ObjectMapper());
    }

    /**
     * The main method for formatting the associated {@link AccessLogEntry} as a Vespa JSON access log string
     *
     * @return The Vespa JSON access log string without trailing newline
     */
    public String format() {
        ByteArrayOutputStream logLine = new ByteArrayOutputStream();
        try {
            JsonGenerator generator = generatorFactory.createGenerator(logLine, JsonEncoding.UTF8);
            generator.writeStartObject();
            generator.writeStringField("ip", accessLogEntry.getIpV4Address());
            generator.writeNumberField("time", toTimestampInSeconds(accessLogEntry.getTimeStampMillis()));
            generator.writeNumberField("duration", durationAsSeconds(accessLogEntry.getDurationBetweenRequestResponseMillis()));
            generator.writeNumberField("responsesize", accessLogEntry.getReturnedContentSize());
            generator.writeNumberField("code", accessLogEntry.getStatusCode());
            generator.writeStringField("method", accessLogEntry.getHttpMethod());
            generator.writeStringField("uri", getNormalizedURI(accessLogEntry.getRawPath(), accessLogEntry.getRawQuery().orElse(null)));
            generator.writeStringField("version", accessLogEntry.getHttpVersion());
            generator.writeStringField("agent", accessLogEntry.getUserAgent());
            generator.writeStringField("host", accessLogEntry.getHostString());
            generator.writeStringField("scheme", accessLogEntry.getScheme());
            generator.writeNumberField("localport", accessLogEntry.getLocalPort());

            Principal principal = accessLogEntry.getUserPrincipal();
            if (principal != null) {
                generator.writeStringField("user-principal", principal.getName());
            }

            Principal sslPrincipal = accessLogEntry.getSslPrincipal();
            if (sslPrincipal != null) {
                generator.writeStringField("ssl-principal", sslPrincipal.getName());
            }

            // Only add remote address/port fields if relevant
            if (remoteAddressDiffers(accessLogEntry.getIpV4Address(), accessLogEntry.getRemoteAddress())) {
                generator.writeStringField("remoteaddr", accessLogEntry.getRemoteAddress());
                if (accessLogEntry.getRemotePort() > 0) {
                    generator.writeNumberField("remoteport", accessLogEntry.getRemotePort());
                }
            }

            // Only add peer address/port fields if relevant
            if (accessLogEntry.getPeerAddress() != null) {
                generator.writeStringField("peeraddr", accessLogEntry.getPeerAddress());

                int peerPort = accessLogEntry.getPeerPort();
                if (peerPort > 0 && peerPort != accessLogEntry.getRemotePort()) {
                    generator.writeNumberField("peerport", peerPort);
                }
            }

            TraceNode trace = accessLogEntry.getTrace();
            if (trace != null) {
                long timestamp = trace.timestamp();
                if (timestamp == 0L) {
                    timestamp = accessLogEntry.getTimeStampMillis();
                }
                trace.accept(new TraceRenderer(generator, timestamp));
            }

            // Only add search sub block of this is a search request
            if (isSearchRequest(accessLogEntry)) {
                generator.writeObjectFieldStart("search");
                generator.writeNumberField("totalhits", getTotalHitCount(accessLogEntry.getHitCounts()));
                generator.writeNumberField("hits", getRetrievedHitCount(accessLogEntry.getHitCounts()));
                Coverage c = accessLogEntry.getHitCounts().getCoverage();
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
            Map<String,List<String>> keyValues = accessLogEntry.getKeyValues();
            if (keyValues != null && !keyValues.isEmpty()) {
                generator.writeObjectFieldStart("attributes");
                for (Map.Entry<String,List<String>> entry : keyValues.entrySet()) {
                    if (entry.getValue().size() == 1) {
                        generator.writeStringField(entry.getKey(), entry.getValue().get(0));
                    } else {
                        generator.writeFieldName(entry.getKey());
                        generator.writeStartArray();
                        for (String s : entry.getValue()) {
                            generator.writeString(s);
                        }
                        generator.writeEndArray();
                    }
                }
                generator.writeEndObject();
            }

            generator.writeEndObject();
            generator.close();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to generate JSON access log entry: " + e.getMessage());
        }

        return logLine.toString();
    }


    private boolean remoteAddressDiffers(String ipV4Address, String remoteAddress) {
        return remoteAddress != null && !Objects.equals(ipV4Address, remoteAddress);
    }

    private boolean isSearchRequest(AccessLogEntry logEntry) {
        return logEntry != null && (logEntry.getHitCounts() != null);
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

    private BigDecimal toTimestampInSeconds(long numMillisSince1Jan1970AtMidnightUTC) {
        BigDecimal timestampInSeconds =
            new BigDecimal(numMillisSince1Jan1970AtMidnightUTC).divide(BigDecimal.valueOf(1000));

        if (numMillisSince1Jan1970AtMidnightUTC/1000 > 0x7fffffff) {
            logger.log(Level.WARNING, "A year 2038 problem occurred.");
            logger.log(Level.INFO, "numMillisSince1Jan1970AtMidnightUTC: "
                       + numMillisSince1Jan1970AtMidnightUTC);
            timestampInSeconds =
                new BigDecimal(numMillisSince1Jan1970AtMidnightUTC)
                    .divide(BigDecimal.valueOf(1000))
                    .remainder(BigDecimal.valueOf(0x7fffffff));
        }
        return timestampInSeconds.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal durationAsSeconds(long timeInMillis) {
        BigDecimal duration =
            new BigDecimal(timeInMillis).divide(BigDecimal.valueOf(1000));

        if (timeInMillis > 0xffffffffL) {
            logger.log(Level.WARNING, "Duration too long: " + timeInMillis);
            duration = new BigDecimal(0xffffffff);
        }

        return duration.setScale(3, RoundingMode.HALF_UP);
    }

    private static String getNormalizedURI(String rawPath, String rawQuery) {
        return rawQuery != null ? rawPath + "?" + rawQuery : rawPath;
    }

}
