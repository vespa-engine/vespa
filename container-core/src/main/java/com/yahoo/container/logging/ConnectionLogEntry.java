// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author mortent
 */
public class ConnectionLogEntry {

    private final UUID id;
    private final Instant timestamp;
    private final Double durationSeconds;
    private final String peerAddress;
    private final Integer peerPort;
    private final String localAddress;
    private final Integer localPort;
    private final String remoteAddress;
    private final Integer remotePort;
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
    private final String sslPeerIssuerSubject;
    private final String sslPeerFingerprint;
    private final String sslSniServerName;
    private final SslHandshakeFailure sslHandshakeFailure;
    private final List<String> sslSubjectAlternativeNames;
    private final String httpProtocol;
    private final String proxyProtocolVersion;
    private final Long sslBytesReceived;
    private final Long sslBytesSent;


    private ConnectionLogEntry(Builder builder) {
        this.id = builder.id;
        this.timestamp = builder.timestamp;
        this.durationSeconds = builder.durationSeconds;
        this.peerAddress = builder.peerAddress;
        this.peerPort = builder.peerPort;
        this.localAddress = builder.localAddress;
        this.localPort = builder.localPort;
        this.remoteAddress = builder.remoteAddress;
        this.remotePort = builder.remotePort;
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
        this.sslPeerIssuerSubject = builder.sslPeerIssuerSubject;
        this.sslPeerFingerprint = builder.sslPeerFingerprint;
        this.sslSniServerName = builder.sslSniServerName;
        this.sslHandshakeFailure = builder.sslHandshakeFailure;
        this.sslSubjectAlternativeNames = builder.sslSubjectAlternativeNames;
        this.httpProtocol = builder.httpProtocol;
        this.proxyProtocolVersion = builder.proxyProtocolVersion;
        this.sslBytesReceived = builder.sslBytesReceived;
        this.sslBytesSent = builder.sslBytesSent;
    }

    public static Builder builder(UUID id, Instant timestamp) {
        return new Builder(id, timestamp);
    }

    public String id() { return id.toString(); }
    public Instant timestamp() { return timestamp; }
    public Optional<Double> durationSeconds() { return Optional.ofNullable(durationSeconds); }
    public Optional<String> peerAddress() { return Optional.ofNullable(peerAddress); }
    public Optional<Integer> peerPort() { return Optional.ofNullable(peerPort); }
    public Optional<String> localAddress() { return Optional.ofNullable(localAddress); }
    public Optional<Integer> localPort() { return Optional.ofNullable(localPort); }
    public Optional<String> remoteAddress() { return Optional.ofNullable(remoteAddress); }
    public Optional<Integer> remotePort() { return Optional.ofNullable(remotePort); }
    public Optional<Long> httpBytesReceived() { return Optional.ofNullable(httpBytesReceived); }
    public Optional<Long> httpBytesSent() { return Optional.ofNullable(httpBytesSent); }
    public Optional<Long> requests() { return Optional.ofNullable(requests); }
    public Optional<Long> responses() { return Optional.ofNullable(responses); }
    public Optional<String> sslSessionId() { return Optional.ofNullable(sslSessionId); }
    public Optional<String> sslProtocol() { return Optional.ofNullable(sslProtocol); }
    public Optional<String> sslCipherSuite() { return Optional.ofNullable(sslCipherSuite); }
    public Optional<String> sslPeerSubject() { return Optional.ofNullable(sslPeerSubject); }
    public Optional<Instant> sslPeerNotBefore() { return Optional.ofNullable(sslPeerNotBefore); }
    public Optional<Instant> sslPeerNotAfter() { return Optional.ofNullable(sslPeerNotAfter); }
    public Optional<String> sslPeerIssuerSubject() { return Optional.ofNullable(sslPeerIssuerSubject); }
    public Optional<String> sslPeerFingerprint() { return Optional.ofNullable(sslPeerFingerprint); }
    public Optional<String> sslSniServerName() { return Optional.ofNullable(sslSniServerName); }
    public Optional<SslHandshakeFailure> sslHandshakeFailure() { return Optional.ofNullable(sslHandshakeFailure); }
    public List<String> sslSubjectAlternativeNames() { return sslSubjectAlternativeNames == null ? List.of() : sslSubjectAlternativeNames; }
    public Optional<String> httpProtocol() { return Optional.ofNullable(httpProtocol); }
    public Optional<String> proxyProtocolVersion() { return Optional.ofNullable(proxyProtocolVersion); }
    public Optional<Long> sslBytesReceived() { return Optional.ofNullable(sslBytesReceived); }
    public Optional<Long> sslBytesSent() { return Optional.ofNullable(sslBytesSent); }

    public static class SslHandshakeFailure {
        private final String type;
        private final List<ExceptionEntry> exceptionChain;

        public SslHandshakeFailure(String type, List<ExceptionEntry> exceptionChain) {
            this.type = type;
            this.exceptionChain = List.copyOf(exceptionChain);
        }

        public String type() { return type; }
        public List<ExceptionEntry> exceptionChain() { return exceptionChain; }

        public static class ExceptionEntry {
            private final String name;
            private final String message;

            public ExceptionEntry(String name, String message) {
                this.name = name;
                this.message = message;
            }

            public String name() { return name; }
            public String message() { return message; }
        }
    }

    public static class Builder {
        private final UUID id;
        private final Instant timestamp;
        private Double durationSeconds;
        private String peerAddress;
        private Integer peerPort;
        private String localAddress;
        private Integer localPort;
        private String remoteAddress;
        private Integer remotePort;
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
        private String sslPeerIssuerSubject;
        private String sslPeerFingerprint;
        private String sslSniServerName;
        private SslHandshakeFailure sslHandshakeFailure;
        private List<String> sslSubjectAlternativeNames;
        private String httpProtocol;
        private String proxyProtocolVersion;
        private Long sslBytesReceived;
        private Long sslBytesSent;


        Builder(UUID id, Instant timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }

        public Builder withDuration(double durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
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
        public Builder withRemoteAddress(String remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }
        public Builder withRemotePort(int remotePort) {
            this.remotePort = remotePort;
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
        public Builder withSslPeerIssuerSubject(String value) {
            this.sslPeerIssuerSubject = value;
            return this;
        }
        public Builder withSslPeerFingerprint(String value) {
            this.sslPeerFingerprint = value;
            return this;
        }
        public Builder withSslSniServerName(String sslSniServerName) {
            this.sslSniServerName = sslSniServerName;
            return this;
        }
        public Builder withSslHandshakeFailure(SslHandshakeFailure sslHandshakeFailure) {
            this.sslHandshakeFailure = sslHandshakeFailure;
            return this;
        }
        public Builder withSslSubjectAlternativeNames(List<String> sslSubjectAlternativeNames) {
            this.sslSubjectAlternativeNames = sslSubjectAlternativeNames;
            return this;
        }
        public Builder withHttpProtocol(String protocol) {
            this.httpProtocol = protocol;
            return this;
        }
        public Builder withProxyProtocolVersion(String version) {
            this.proxyProtocolVersion = version;
            return this;
        }
        public Builder withSslBytesReceived(long bytesReceived) {
            this.sslBytesReceived = bytesReceived;
            return this;
        }
        public Builder withSslBytesSent(long bytesSent) {
            this.sslBytesSent = bytesSent;
            return this;
        }

        public ConnectionLogEntry build(){
            return new ConnectionLogEntry(this);
        }

    }
}
