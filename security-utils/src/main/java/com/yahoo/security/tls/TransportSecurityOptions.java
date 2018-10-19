// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic TLS configuration for Vespa
 *
 * @author bjorncs
 */
public class TransportSecurityOptions {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Path privateKeyFile;
    private final Path certificatesFile;
    private final Path caCertificatesFile;

    public TransportSecurityOptions(String privateKeyFile, String certificatesFile, String caCertificatesFile) {
        this(Paths.get(privateKeyFile), Paths.get(certificatesFile), Paths.get(caCertificatesFile));
    }

    public TransportSecurityOptions(Path privateKeyFile, Path certificatesFile, Path caCertificatesFile) {
        this.privateKeyFile = privateKeyFile;
        this.certificatesFile = certificatesFile;
        this.caCertificatesFile = caCertificatesFile;
    }

    public Path getPrivateKeyFile() {
        return privateKeyFile;
    }

    public Path getCertificatesFile() {
        return certificatesFile;
    }

    public Path getCaCertificatesFile() {
        return caCertificatesFile;
    }

    public static TransportSecurityOptions fromJsonFile(Path file) {
        try {
            JsonNode root = mapper.readTree(file.toFile());
            JsonNode filesNode = getField(root, "files");
            String privateKeyFile = getField(filesNode, "private-key").asText();
            String certificatesFile = getField(filesNode, "certificates").asText();
            String caCertificatesFile = getField(filesNode, "ca-certificates").asText();
            return new TransportSecurityOptions(privateKeyFile, certificatesFile, caCertificatesFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode getField(JsonNode root, String fieldName) {
        return Optional.ofNullable(root.get(fieldName))
                .orElseThrow(() -> new IllegalArgumentException(String.format("'%s' field missing", fieldName)));
    }

    @Override
    public String toString() {
        return "TransportSecurityOptions{" +
                "privateKeyFile=" + privateKeyFile +
                ", certificatesFile=" + certificatesFile +
                ", caCertificatesFile=" + caCertificatesFile +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransportSecurityOptions that = (TransportSecurityOptions) o;
        return Objects.equals(privateKeyFile, that.privateKeyFile) &&
                Objects.equals(certificatesFile, that.certificatesFile) &&
                Objects.equals(caCertificatesFile, that.caCertificatesFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(privateKeyFile, certificatesFile, caCertificatesFile);
    }
}