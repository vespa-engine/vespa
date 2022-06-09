// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.compress.CompressionType;


public class CompressionConfig {

    public CompressionConfig(CompressionType type,
                             int level,
                             float threshold,
                             long minSize)
    {
        this.type = type;
        this.compressionLevel = level;
        this.threshold = threshold;
        this.minsize = minSize;
    }

    public CompressionConfig() {
        this(CompressionType.NONE, 9, 95, 0);
    }

    public CompressionConfig(CompressionType type) {
        this(type, 9, 95, 0);
    }

    public CompressionConfig(CompressionType type, int level, float threshold) {
        this(type, level, threshold, 0);
    }

    public final CompressionType type;
    public int compressionLevel;
    public float threshold;
    public final long minsize;

    /** get a multiplier for comparing compressed and original size */
    public float thresholdFactor() { return 0.01f * threshold; }
}
