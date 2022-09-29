// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.application.api.FileRegistry;

import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;

public class RankingExpressionBody extends DistributableResource {

    private final ByteBuffer blob;

    public RankingExpressionBody(String name, ByteBuffer blob) {
        super(name, name + ".lz4", PathType.BLOB);
        this.blob = requireNonNull(blob, "Blob cannot be null");
    }

    public ByteBuffer getBlob() { return blob; }

    public void validate() {
        // Remove once pathType is final
        if (getPathType() != PathType.BLOB) {
            throw new IllegalArgumentException("PathType must be BLOB.");
        }
    }

    public void register(FileRegistry fileRegistry) {
        register(fileRegistry, blob);
    }

}
