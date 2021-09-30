// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.tls.json.TransportSecurityOptionsJsonSerializer;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic TLS configuration for Vespa
 *
 * @author bjorncs
 */
public class TransportSecurityOptions {

    private final Path privateKeyFile;
    private final Path certificatesFile;
    private final Path caCertificatesFile;
    private final AuthorizedPeers authorizedPeers;
    private final List<String> acceptedCiphers;
    private final List<String> acceptedProtocols;
    private final boolean isHostnameValidationDisabled;

    private TransportSecurityOptions(Builder builder) {
        this.privateKeyFile = builder.privateKeyFile;
        this.certificatesFile = builder.certificatesFile;
        this.caCertificatesFile = builder.caCertificatesFile;
        this.authorizedPeers = builder.authorizedPeers;
        this.acceptedCiphers = builder.acceptedCiphers;
        this.acceptedProtocols = builder.acceptedProtocols;
        this.isHostnameValidationDisabled = builder.isHostnameValidationDisabled;
    }

    public Optional<Path> getPrivateKeyFile() {
        return Optional.ofNullable(privateKeyFile);
    }

    public Optional<Path> getCertificatesFile() {
        return Optional.ofNullable(certificatesFile);
    }

    public Optional<Path> getCaCertificatesFile() {
        return Optional.ofNullable(caCertificatesFile);
    }

    public Optional<AuthorizedPeers> getAuthorizedPeers() {
        return Optional.ofNullable(authorizedPeers);
    }

    public List<String> getAcceptedCiphers() { return acceptedCiphers; }

    public List<String> getAcceptedProtocols() { return acceptedProtocols; }

    public boolean isHostnameValidationDisabled() { return isHostnameValidationDisabled; }

    public static TransportSecurityOptions fromJsonFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return new TransportSecurityOptionsJsonSerializer().deserialize(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static TransportSecurityOptions fromJson(String json) {
        return new TransportSecurityOptionsJsonSerializer()
                .deserialize(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    public String toJson() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TransportSecurityOptionsJsonSerializer().serialize(out, this);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public void toJsonFile(Path file) {
        try (OutputStream out = Files.newOutputStream(file)) {
            new TransportSecurityOptionsJsonSerializer().serialize(out, this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Builder {
        private Path privateKeyFile;
        private Path certificatesFile;
        private Path caCertificatesFile;
        private AuthorizedPeers authorizedPeers;
        private List<String> acceptedCiphers = new ArrayList<>();
        private boolean isHostnameValidationDisabled;
        private List<String> acceptedProtocols = new ArrayList<>();

        public Builder() {}

        public Builder withCertificates(Path certificatesFile, Path privateKeyFile) {
            this.certificatesFile = certificatesFile;
            this.privateKeyFile = privateKeyFile;
            return this;
        }

        public Builder withCaCertificates(Path caCertificatesFile) {
            this.caCertificatesFile = caCertificatesFile;
            return this;
        }

        public Builder withAuthorizedPeers(AuthorizedPeers authorizedPeers) {
            this.authorizedPeers = authorizedPeers;
            return this;
        }

        public Builder withAcceptedCiphers(List<String> acceptedCiphers) {
            this.acceptedCiphers = acceptedCiphers;
            return this;
        }

        public Builder withAcceptedProtocols(List<String> acceptedProtocols) {
            this.acceptedProtocols = acceptedProtocols;
            return this;
        }

        public Builder withHostnameValidationDisabled(boolean isDisabled) {
            this.isHostnameValidationDisabled = isDisabled;
            return this;
        }

        public TransportSecurityOptions build() {
            return new TransportSecurityOptions(this);
        }
    }

    @Override
    public String toString() {
        return "TransportSecurityOptions{" +
                "privateKeyFile=" + privateKeyFile +
                ", certificatesFile=" + certificatesFile +
                ", caCertificatesFile=" + caCertificatesFile +
                ", authorizedPeers=" + authorizedPeers +
                ", acceptedCiphers=" + acceptedCiphers +
                ", acceptedProtocols=" + acceptedProtocols +
                ", isHostnameValidationDisabled=" + isHostnameValidationDisabled +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransportSecurityOptions that = (TransportSecurityOptions) o;
        return isHostnameValidationDisabled == that.isHostnameValidationDisabled &&
                Objects.equals(privateKeyFile, that.privateKeyFile) &&
                Objects.equals(certificatesFile, that.certificatesFile) &&
                Objects.equals(caCertificatesFile, that.caCertificatesFile) &&
                Objects.equals(authorizedPeers, that.authorizedPeers) &&
                Objects.equals(acceptedCiphers, that.acceptedCiphers) &&
                Objects.equals(acceptedProtocols, that.acceptedProtocols);
    }

    @Override
    public int hashCode() {
        return Objects.hash(privateKeyFile, certificatesFile, caCertificatesFile, authorizedPeers, acceptedCiphers,
                acceptedProtocols, isHostnameValidationDisabled);
    }
}