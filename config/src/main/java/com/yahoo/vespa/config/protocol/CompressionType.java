// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

/**
 * @author Ulf Lilleengen
 */
public enum CompressionType {
    UNCOMPRESSED, LZ4;
    public static CompressionType parse(String value) {
        for (CompressionType type : CompressionType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return CompressionType.UNCOMPRESSED;
    }
}
