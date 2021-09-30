// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

/**
 * Compression type enum.
 *
 * @author bratseth
 */
public enum CompressionType {

    // Do not change the type->ordinal association. The gap is due to historic types no longer supported.
    NONE((byte) 0),
    INCOMPRESSIBLE((byte) 5),
    LZ4((byte) 6),
    ZSTD((byte) 7);

    private byte code;

    CompressionType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    /**
     * Returns whether this type represent actually compressed data
     */
    public boolean isCompressed() {
        return this != NONE && this != INCOMPRESSIBLE;
    }

    public static CompressionType valueOf(byte value) {
        switch (value) {
            case ((byte) 0):
                return NONE;
            case ((byte) 5):
                return INCOMPRESSIBLE;
            case ((byte) 6):
                return LZ4;
            case ((byte) 7):
                return ZSTD;
            default:
                throw new IllegalArgumentException("Unknown compression type ordinal " + value);
        }
    }

}
