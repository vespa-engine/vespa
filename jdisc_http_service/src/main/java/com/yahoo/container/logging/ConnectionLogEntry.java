// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * @author mortent
 */
public class ConnectionLogEntry {

    private final UUID id;
    private final Instant timestamp;
    private final String peerAddress;
    private final Integer peerPort;
    private final String localAddress;
    private final Integer localPort;
    private final Long bytesReceived;
    private final Long bytesSent;
    private final Long requests;
    private final Long responses;
    private final String sslSessionId;
    private final String sslProtocol;
    private final String sslCipherSuite;
    private final String sslPeerSubject;
    private final Instant sslPeerNotBefore;
    private final Instant sslPeerNotAfter;
    private final String sslSniServerName;

    private ConnectionLogEntry(Builder builder) {
        this.id = builder.id;
        this.timestamp = builder.timestamp;
        this.peerAddress = builder.peerAddress;
        this.peerPort = builder.peerPort;
        this.localAddress = builder.localAddress;
        this.localPort = builder.localPort;
        this.bytesReceived = builder.bytesReceived;
        this.bytesSent = builder.bytesSent;
        this.requests = builder.requests;
        this.responses = builder.responses;
        this.sslSessionId = builder.sslSessionId;
        this.sslProtocol = builder.sslProtocol;
        this.sslCipherSuite = builder.sslCipherSuite;
        this.sslPeerSubject = builder.sslPeerSubject;
        this.sslPeerNotBefore = builder.sslPeerNotBefore;
        this.sslPeerNotAfter = builder.sslPeerNotAfter;
        this.sslSniServerName = builder.sslSniServerName;
    }

    public String toJson() {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("id", id.toString());
        setTimestamp(cursor, "timestamp", timestamp);

        setString(cursor, "peerAddress", peerAddress);
        setInteger(cursor, "peerPort", peerPort);
        setString(cursor, "localAddress", localAddress);
        setInteger(cursor, "localPort", localPort);
        setLong(cursor, "bytesReceived", bytesReceived);
        setLong(cursor, "bytesSent", bytesSent);
        setLong(cursor, "requests", requests);
        setLong(cursor, "responses", responses);
        if (sslProtocol != null) {
            Cursor sslCursor = cursor.setObject("ssl");
            setString(sslCursor, "protocol", sslProtocol);
            setString(sslCursor, "sessionId", sslSessionId);
            setString(sslCursor, "cipherSuite", sslCipherSuite);
            setString(sslCursor, "peerSubject", sslPeerSubject);
            setTimestamp(sslCursor, "peerNotBefore", sslPeerNotBefore);
            setTimestamp(sslCursor, "peerNotAfter", sslPeerNotAfter);
            setString(sslCursor, "sniServerName", sslSniServerName);
        }
        return new String(Exceptions.uncheck(() -> SlimeUtils.toJsonBytes(slime)), StandardCharsets.UTF_8);
    }

    private void setString(Cursor cursor, String key, String value) {
        if(value != null) {
            cursor.setString(key, value);
        }
    }

    private void setLong(Cursor cursor, String key, Long value) {
        if (value != null) {
            cursor.setLong(key, value);
        }
    }

    private void setInteger(Cursor cursor, String key, Integer value) {
        if (value != null) {
            cursor.setLong(key, value);
        }
    }

    private void setTimestamp(Cursor cursor, String key, Instant timestamp) {
        if (timestamp != null) {
            cursor.setString(key, timestamp.toString());
        }
    }

    public static Builder builder(UUID id, Instant timestamp) {
        return new Builder(id, timestamp);
    }

    public String id() {
        return id.toString();
    }

    public static class Builder {
        private final UUID id;
        private final Instant timestamp;
        private String peerAddress;
        private Integer peerPort;
        private String localAddress;
        private Integer localPort;
        private Long bytesReceived;
        private Long bytesSent;
        private Long requests;
        private Long responses;
        private String sslSessionId;
        private String sslProtocol;
        private String sslCipherSuite;
        private String sslPeerSubject;
        private Instant sslPeerNotBefore;
        private Instant sslPeerNotAfter;
        private String sslSniServerName;

        Builder(UUID id, Instant timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }

        public Builder withPeerAddress(String peerAddress) {
            this.peerAddress = peerAddress;
            return this;
        }
        public Builder withPeerPort(int peerPort) {
            this.peerPort = peerPort;
            return this;
        }
        public Builder withLocalAddress(String localAddress) {
            this.localAddress = localAddress;
            return this;
        }
        public Builder withLocalPort(int localPort) {
            this.localPort = localPort;
            return this;
        }
        public Builder withBytesReceived(long bytesReceived) {
            this.bytesReceived = bytesReceived;
            return this;
        }
        public Builder withBytesSent(long bytesSent) {
            this.bytesSent = bytesSent;
            return this;
        }
        public Builder withRequests(long requests) {
            this.requests = requests;
            return this;
        }
        public Builder withResponses(long responses) {
            this.responses = responses;
            return this;
        }
        public Builder withSslSessionId(String sslSessionId) {
            this.sslSessionId = sslSessionId;
            return this;
        }
        public Builder withSslProtocol(String sslProtocol) {
            this.sslProtocol = sslProtocol;
            return this;
        }
        public Builder withSslCipherSuite(String sslCipherSuite) {
            this.sslCipherSuite = sslCipherSuite;
            return this;
        }
        public Builder withSslPeerSubject(String sslPeerSubject) {
            this.sslPeerSubject = sslPeerSubject;
            return this;
        }
        public Builder withSslPeerNotBefore(Instant sslPeerNotBefore) {
            this.sslPeerNotBefore = sslPeerNotBefore;
            return this;
        }
        public Builder withSslPeerNotAfter(Instant sslPeerNotAfter) {
            this.sslPeerNotAfter = sslPeerNotAfter;
            return this;
        }
        public Builder withSslSniServerName(String sslSniServerName) {
            this.sslSniServerName = sslSniServerName;
            return this;
        }

        public ConnectionLogEntry build(){
            return new ConnectionLogEntry(this);
        }
    }
}
