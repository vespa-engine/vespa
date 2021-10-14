// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

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
    private final ContainerPath file;
    private final boolean compressOnUpload;

    private Artifact(Builder builder) {
        if (builder.file == null) {
            throw new IllegalArgumentException("No file specified");
        }
        this.file = builder.file;
        this.classification = builder.classification;
        this.compressOnUpload = Boolean.TRUE.equals(builder.compressOnUpload);
    }

    static Builder newBuilder() { return new Builder(); }

    Optional<Classification> classification() { return Optional.ofNullable(classification); }
    ContainerPath file() { return file; }
    boolean compressOnUpload() { return compressOnUpload; }

    static class Builder {
        private Classification classification;
        private ContainerPath file;
        private Boolean compressOnUpload;

        private Builder() {}

        Builder classification(Classification c) { this.classification = c; return this; }
        Builder file(ContainerPath f) { this.file = f; return this; }
        Builder compressOnUpload() { this.compressOnUpload = true; return this; }
        Artifact build() { return new Artifact(this); }
    }
}
