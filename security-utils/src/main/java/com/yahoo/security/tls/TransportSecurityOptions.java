// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic TLS configuration for Vespa
 *
 * @author bjorncs
 */
// TODO Add builder
public class TransportSecurityOptions {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Path privateKeyFile;
    private final Path certificatesFile;
    private final Path caCertificatesFile;
    private final List<String> acceptedCiphers;

    public TransportSecurityOptions(String privateKeyFile, String certificatesFile, String caCertificatesFile) {
        this(Paths.get(privateKeyFile), Paths.get(certificatesFile), Paths.get(caCertificatesFile));
    }

    public TransportSecurityOptions(Path privateKeyFile, Path certificatesFile, Path caCertificatesFile) {
        this(privateKeyFile, certificatesFile, caCertificatesFile, Collections.emptyList());
    }

    public TransportSecurityOptions(String privateKeyFile, String certificatesFile, String caCertificatesFile, List<String> acceptedCiphers) {
        this(Paths.get(privateKeyFile), Paths.get(certificatesFile), Paths.get(caCertificatesFile), acceptedCiphers);
    }

    public TransportSecurityOptions(Path privateKeyFile, Path certificatesFile, Path caCertificatesFile, List<String> acceptedCiphers) {
        this.privateKeyFile = privateKeyFile;
        this.certificatesFile = certificatesFile;
        this.caCertificatesFile = caCertificatesFile;
        this.acceptedCiphers = acceptedCiphers;
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

    public List<String> getAcceptedCiphers() {
        return acceptedCiphers;
    }

    public static TransportSecurityOptions fromJsonFile(Path file) {
        try {
            return fromJsonNode(mapper.readTree(file.toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static TransportSecurityOptions fromJson(String json) {
        try {
            return fromJsonNode(mapper.readTree(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TransportSecurityOptions fromJsonNode(JsonNode root) {
        JsonNode filesNode = getFieldOrThrow(root, "files");
        List<String> acceptedCiphers = getField(root, "accepted-ciphers")
                .map(TransportSecurityOptions::toCipherList)
                .orElse(Collections.emptyList());
        String privateKeyFile = getFieldOrThrow(filesNode, "private-key").asText();
        String certificatesFile = getFieldOrThrow(filesNode, "certificates").asText();
        String caCertificatesFile = getFieldOrThrow(filesNode, "ca-certificates").asText();
        return new TransportSecurityOptions(privateKeyFile, certificatesFile, caCertificatesFile, acceptedCiphers);
    }

    private static List<String> toCipherList(JsonNode ciphersNode) {
        List<String> ciphers = new ArrayList<>();
        for (JsonNode cipherNode : ciphersNode) {
            ciphers.add(cipherNode.asText());
        }
        return ciphers;
    }

    private static Optional<JsonNode> getField(JsonNode root, String fieldName) {
        return Optional.ofNullable(root.get(fieldName));
    }

    private static JsonNode getFieldOrThrow(JsonNode root, String fieldName) {
        return getField(root, fieldName)
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