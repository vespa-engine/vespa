// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import java.nio.file.Path;

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
    private final Path file;
    private final boolean compressOnUpload;

    private Artifact(Builder builder) {
        this.file = builder.file;
        this.classification = builder.classification;
        this.compressOnUpload = builder.compressOnUpload != null ? builder.compressOnUpload : false;
    }

    static Builder newBuilder(Classification classification, Path file) {
        return new Builder(classification, file);
    }

    Classification classification() { return classification; }
    Path file() { return file; }
    boolean compressOnUpload() { return compressOnUpload; }

    static class Builder {
        private Classification classification;
        private Path file;
        private Boolean compressOnUpload;

        private Builder(Classification classification, Path file) {
            this.classification = classification;
            this.file = file;
        }

        Builder compressOnUpload() { this.compressOnUpload = true; return this; }
        Artifact build() { return new Artifact(this); }
    }
}
