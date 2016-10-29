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

    // Access log fields set from access log entry
    private String ipV4AddressInDotDecimalNotation;
    private String timestampWithMillis;
    private String durationBetweenRequestResponseInMillis;
    private String numBytesReturned;
    private String statusCode;
    private String uri;
    private String httpVersion;
    private String userAgent;
    private String totalHits;
    private String retrievedHits;
    private String httpMethod;
    private String hostString;
    private String remoteAddress;
    private String remotePort;
    private String peerAddress;
    private String peerPort;

    private Map<String,List<String>> keyValues;

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
        String logString;

        setIpV4Address(accessLogEntry.getIpV4Address());
        setTimeStampMillis(accessLogEntry.getTimeStampMillis());
        setDurationBetweenRequestResponseMillis(accessLogEntry.getDurationBetweenRequestResponseMillis());
        setReturnedContentSize(accessLogEntry.getReturnedContentSize());
        setStatusCode(accessLogEntry.getStatusCode());
        setHitCounts(accessLogEntry.getHitCounts());

        setRemoteAddress(accessLogEntry.getRemoteAddress());
        setRemotePort(accessLogEntry.getRemotePort());

        setHttpMethod(accessLogEntry.getHttpMethod());
        setURI(accessLogEntry.getURI());
        setHttpVersion(accessLogEntry.getHttpVersion());

        setUserAgent(accessLogEntry.getUserAgent());

        setHostString(accessLogEntry.getHostString());
        setPeerAddress(accessLogEntry.getPeerAddress());
        setPeerPort(accessLogEntry.getPeerPort());

        keyValues = accessLogEntry.getKeyValues();

        try {
            logString = toJSONAccessEntry();
        } catch (IOException e) {
            logString = "";
        }

        return logString;
    }

    private String toJSONAccessEntry() throws IOException {
        ByteArrayOutputStream logLine = new ByteArrayOutputStream();
        JsonGenerator generator = generatorFactory.createGenerator(logLine, JsonEncoding.UTF8);
        generator.writeStartObject();
        generator.writeStringField("ip", ipV4AddressInDotDecimalNotation);
        generator.writeStringField("time", timestampWithMillis);
        generator.writeStringField("duration", durationBetweenRequestResponseInMillis);
        generator.writeStringField("size", numBytesReturned);
        generator.writeStringField("code", statusCode);
        generator.writeStringField("totalhits", totalHits);
        generator.writeStringField("hits", retrievedHits);
        generator.writeStringField("method", httpMethod);
        generator.writeStringField("uri", uri);
        generator.writeStringField("version", httpVersion);
        generator.writeStringField("agent", userAgent);
        generator.writeStringField("host", hostString);

        // Only add remote address/port fields if relevant
        if (remoteAddressDiffers(ipV4AddressInDotDecimalNotation, remoteAddress)) {
            generator.writeStringField("remoteaddr", remoteAddress);
            if (remotePort != null) {
                generator.writeStringField("remoteport", remotePort);
            }
        }

        // Only add peer address/port fields if relevant
        if (peerAddress != null) {
            generator.writeStringField("peeraddr", peerAddress);
            if (peerPort != null && !peerPort.equals(remotePort)) {
                generator.writeStringField("peerport", peerPort);
            }
        }

        // Add key/value access log entries. Keys with single values are written as single
        // string value fields while keys with multiple values are written as string arrays
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

        return logLine.toString();
    }

    private boolean remoteAddressDiffers(String ipV4Address, String remoteAddress) {
        return remoteAddress != null && !Objects.equals(ipV4Address, remoteAddress);
    }

    private void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    private void setHttpMethod(String method) { this.httpMethod = method; }

    private void setHttpVersion(String httpVersion) { this.httpVersion = httpVersion; }

    private void setHostString(String hostString) { this.hostString = hostString; }

    private void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }

    private void setPeerAddress(final String peerAddress) { this.peerAddress = peerAddress; }

    private void setRemotePort(int remotePort) {
        if (remotePort == 0) {
            this.remotePort = null;
        } else {
            this.remotePort = String.valueOf(remotePort);
        }
    }

    private void setPeerPort(int peerPort) {
        if (peerPort == 0) {
            this.peerPort = null;
        } else {
            this.peerPort = String.valueOf(peerPort);
        }
    }

    private void setHitCounts(HitCounts counts) {
        if (counts == null) {
            return;
        }

        this.totalHits = String.valueOf(counts.getTotalHitCount());
        this.retrievedHits = String.valueOf(counts.getRetrievedHitCount());
    }

    private void setIpV4Address(String ipV4AddressInDotDecimalNotation) {
        this.ipV4AddressInDotDecimalNotation = ipV4AddressInDotDecimalNotation;
    }

    private void setTimeStampMillis(long numMillisSince1Jan1970AtMidnightUTC) {
        int unixTime = (int)(numMillisSince1Jan1970AtMidnightUTC/1000);
        int milliSeconds = (int)(numMillisSince1Jan1970AtMidnightUTC % 1000);

        if (numMillisSince1Jan1970AtMidnightUTC/1000 > 0x7fffffff) {
            logger.log(Level.WARNING, "A year 2038 problem occurred.");
            logger.log(Level.INFO, "numMillisSince1Jan1970AtMidnightUTC: "
                       + numMillisSince1Jan1970AtMidnightUTC);
            unixTime = (int)(numMillisSince1Jan1970AtMidnightUTC/1000 % 0x7fffffff);
        }

        timestampWithMillis = unixTime + "." + milliSeconds;
    }

    private void setDurationBetweenRequestResponseMillis(long timeInMillis) {
        int duration = (int)timeInMillis;

        if (timeInMillis > 0xffffffffL) {
            logger.log(Level.WARNING, "Duration too long: " + timeInMillis);
            duration = 0xffffffff;
        }

        durationBetweenRequestResponseInMillis = String.valueOf(duration);
    }

    private void setReturnedContentSize(long byteCount) {
        numBytesReturned = String.valueOf(byteCount);
    }

    private void setURI(final URI uri) {
        setNormalizedURI(uri.normalize());
    }

    private void setNormalizedURI(final URI normalizedUri) {
        String uriString = normalizedUri.getPath();
        if (normalizedUri.getRawQuery() != null) {
            uriString = uriString + "?" + normalizedUri.getRawQuery();
        }

        this.uri = uriString;
    }

    private void setStatusCode(int statusCode) {
        if (statusCode == 0) {
            return;
        }

        this.statusCode = String.valueOf(statusCode);
    }

}
