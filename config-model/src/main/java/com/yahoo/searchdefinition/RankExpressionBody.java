// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.FileRegistry;

import java.nio.ByteBuffer;
import java.util.Objects;

public class RankExpressionBody extends DistributableResource {

    private final ByteBuffer blob;

    public RankExpressionBody(String name, ByteBuffer blob) {
        super(name, blob);
        Objects.requireNonNull(blob, "Blob cannot be null");
        this.blob = blob;
    }
    public ByteBuffer getBlob() { return blob; }
    public void validate() {
        // Remove once pathType is final
        if (getPathType() != PathType.BLOB) {
            throw new IllegalArgumentException("PathType must be BLOB.");
        }
    }

    void register(FileRegistry fileRegistry) {
        register(fileRegistry, blob);
    }
}
