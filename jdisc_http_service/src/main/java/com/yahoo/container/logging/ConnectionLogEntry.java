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
    private final Long httpBytesReceived;
    private final Long httpBytesSent;
    private final Long requests;
    private final Long responses;
    private final String sslSessionId;
    private final String sslProtocol;
    private final String sslCipherSuite;
    private final String sslPeerSubject;
    private final Instant sslPeerNotBefore;
    private final Instant sslPeerNotAfter;
    private final String sslSniServerName;
    private final String sslHandshakeFailureException;
    private final String sslHandshakeFailureMessage;
    private final String sslHandshakeFailureType;


    private ConnectionLogEntry(Builder builder) {
        this.id = builder.id;
        this.timestamp = builder.timestamp;
        this.peerAddress = builder.peerAddress;
        this.peerPort = builder.peerPort;
        this.localAddress = builder.localAddress;
        this.localPort = builder.localPort;
        this.httpBytesReceived = builder.httpBytesReceived;
        this.httpBytesSent = builder.httpBytesSent;
        this.requests = builder.requests;
        this.responses = builder.responses;
        this.sslSessionId = builder.sslSessionId;
        this.sslProtocol = builder.sslProtocol;
        this.sslCipherSuite = builder.sslCipherSuite;
        this.sslPeerSubject = builder.sslPeerSubject;
        this.sslPeerNotBefore = builder.sslPeerNotBefore;
        this.sslPeerNotAfter = builder.sslPeerNotAfter;
        this.sslSniServerName = builder.sslSniServerName;
        this.sslHandshakeFailureException = builder.sslHandshakeFailureException;
        this.sslHandshakeFailureMessage = builder.sslHandshakeFailureMessage;
        this.sslHandshakeFailureType = builder.sslHandshakeFailureType;
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
        setLong(cursor, "httpBytesReceived", httpBytesReceived);
        setLong(cursor, "httpBytesSent", httpBytesSent);
        setLong(cursor, "requests", requests);
        setLong(cursor, "responses", responses);
        if (sslProtocol != null || sslHandshakeFailureException != null) {
            Cursor sslCursor = cursor.setObject("ssl");
            setString(sslCursor, "protocol", sslProtocol);
            setString(sslCursor, "sessionId", sslSessionId);
            setString(sslCursor, "cipherSuite", sslCipherSuite);
            setString(sslCursor, "peerSubject", sslPeerSubject);
            setTimestamp(sslCursor, "peerNotBefore", sslPeerNotBefore);
            setTimestamp(sslCursor, "peerNotAfter", sslPeerNotAfter);
            setString(sslCursor, "sniServerName", sslSniServerName);
            if (sslHandshakeFailureException != null) {
                Cursor handshakeFailureCursor = sslCursor.setObject("handshake-failure");
                setString(handshakeFailureCursor, "exception", sslHandshakeFailureException);
                setString(handshakeFailureCursor, "message", sslHandshakeFailureMessage);
                setString(handshakeFailureCursor, "type", sslHandshakeFailureType);
            }
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
        private Long httpBytesReceived;
        private Long httpBytesSent;
        private Long requests;
        private Long responses;
        private String sslSessionId;
        private String sslProtocol;
        private String sslCipherSuite;
        private String sslPeerSubject;
        private Instant sslPeerNotBefore;
        private Instant sslPeerNotAfter;
        private String sslSniServerName;
        private String sslHandshakeFailureException;
        private String sslHandshakeFailureMessage;
        private String sslHandshakeFailureType;


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
        public Builder withHttpBytesReceived(long bytesReceived) {
            this.httpBytesReceived = bytesReceived;
            return this;
        }
        public Builder withHttpBytesSent(long bytesSent) {
            this.httpBytesSent = bytesSent;
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
        public Builder withSslHandshakeFailureException(String exception) {
            this.sslHandshakeFailureException = exception;
            return this;
        }
        public Builder withSslHandshakeFailureMessage(String message) {
            this.sslHandshakeFailureMessage = message;
            return this;
        }
        public Builder withSslHandshakeFailureType(String type) {
            this.sslHandshakeFailureType = type;
            return this;
        }

        public ConnectionLogEntry build(){
            return new ConnectionLogEntry(this);
        }
    }
}
