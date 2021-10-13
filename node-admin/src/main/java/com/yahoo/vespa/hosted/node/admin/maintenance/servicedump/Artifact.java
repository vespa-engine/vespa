// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import java.nio.file.Path;
import java.util.Optional;

/**
 * An artifact file produced by a {@link ArtifactProducer}.
 *
 * @author bjorncs
 */
class Artifact {

    enum Classification {
        CONFIDENTIAL("confidential"),
        INTERNAL("internal");

        private final String value;
        Classification(String value) { this.value = value; }
        public String value() { return value; }
    }

    private final Classification classification;
    private final Path fileInNode;
    private final Path fileOnHost;
    private final boolean compressOnUpload;

    private Artifact(Builder builder) {
        if (builder.fileOnHost == null && builder.fileInNode == null) {
            throw new IllegalArgumentException("No file specified");
        } else if (builder.fileOnHost != null && builder.fileInNode != null) {
            throw new IllegalArgumentException("Only a single file can be specified");
        }
        this.fileInNode = builder.fileInNode;
        this.fileOnHost = builder.fileOnHost;
        this.classification = builder.classification;
        this.compressOnUpload = Boolean.TRUE.equals(builder.compressOnUpload);
    }

    static Builder newBuilder() { return new Builder(); }

    Optional<Classification> classification() { return Optional.ofNullable(classification); }
    Optional<Path> fileInNode() { return Optional.ofNullable(fileInNode); }
    Optional<Path> fileOnHost() { return Optional.ofNullable(fileOnHost); }
    boolean compressOnUpload() { return compressOnUpload; }

    static class Builder {
        private Classification classification;
        private Path fileInNode;
        private Path fileOnHost;
        private Boolean compressOnUpload;

        private Builder() {}

        Builder classification(Classification c) { this.classification = c; return this; }
        Builder fileInNode(Path f) { this.fileInNode = f; return this; }
        Builder fileOnHost(Path f) { this.fileOnHost = f; return this; }
        Builder compressOnUpload() { this.compressOnUpload = true; return this; }
        Artifact build() { return new Artifact(this); }
    }
}
