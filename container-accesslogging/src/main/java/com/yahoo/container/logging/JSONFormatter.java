// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
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
            generator.writeStringField("time", toTimestampWithFraction(accessLogEntry.getTimeStampMillis()));
            generator.writeNumberField("duration",
                                       capDuration(accessLogEntry.getDurationBetweenRequestResponseMillis()));
            generator.writeNumberField("size", accessLogEntry.getReturnedContentSize());
            generator.writeNumberField("code", accessLogEntry.getStatusCode());
            generator.writeNumberField("totalhits", getTotalHitCount(accessLogEntry.getHitCounts()));
            generator.writeNumberField("hits", getRetrievedHitCount(accessLogEntry.getHitCounts()));
            generator.writeStringField("method", accessLogEntry.getHttpMethod());
            generator.writeStringField("uri", getNormalizedURI(accessLogEntry.getURI()));
            generator.writeStringField("version", accessLogEntry.getHttpVersion());
            generator.writeStringField("agent", accessLogEntry.getUserAgent());
            generator.writeStringField("host", accessLogEntry.getHostString());

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

    private String toTimestampWithFraction(long numMillisSince1Jan1970AtMidnightUTC) {
        int unixTime = (int)(numMillisSince1Jan1970AtMidnightUTC/1000);
        int milliSeconds = (int)(numMillisSince1Jan1970AtMidnightUTC % 1000);

        if (numMillisSince1Jan1970AtMidnightUTC/1000 > 0x7fffffff) {
            logger.log(Level.WARNING, "A year 2038 problem occurred.");
            logger.log(Level.INFO, "numMillisSince1Jan1970AtMidnightUTC: "
                       + numMillisSince1Jan1970AtMidnightUTC);
            unixTime = (int)(numMillisSince1Jan1970AtMidnightUTC/1000 % 0x7fffffff);
        }

        return unixTime + "." + milliSeconds;
    }

    private int capDuration(long timeInMillis) {
        int duration = (int)timeInMillis;

        if (timeInMillis > 0xffffffffL) {
            logger.log(Level.WARNING, "Duration too long: " + timeInMillis);
            duration = 0xffffffff;
        }

        return duration;
    }

    private String getNormalizedURI(URI uri) {
        URI normalizedURI = uri.normalize();
        String uriString = normalizedURI.getPath();
        if (normalizedURI.getRawQuery() != null) {
            uriString = uriString + "?" + normalizedURI.getRawQuery();
        }

        return uriString;
    }

}
