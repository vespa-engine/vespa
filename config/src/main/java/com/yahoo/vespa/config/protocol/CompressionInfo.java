// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.slime.Inspector;

import java.io.IOException;

/**
 * Contains info relevant for compression and decompression.
 *
 * @author Ulf Lilleengen
 */
public class CompressionInfo {
    private static final String COMPRESSION_TYPE = "compressionType";
    private static final String UNCOMPRESSED_SIZE = "uncompressedSize";

    public CompressionType getCompressionType() {
        return compressionType;
    }
    public int getUncompressedSize() {
        return uncompressedSize;
    }

    private final CompressionType compressionType;
    private final int uncompressedSize;

    private CompressionInfo(CompressionType compressionType, int uncompressedSize) {
        this.compressionType = compressionType;
        this.uncompressedSize = uncompressedSize;
    }

    public static CompressionInfo uncompressed() {
        return new CompressionInfo(CompressionType.UNCOMPRESSED, 0);
    }

    public static CompressionInfo create(CompressionType type, int uncompressedSize) {
        return new CompressionInfo(type, uncompressedSize);
    }

    public static CompressionInfo fromSlime(Inspector field) {
        CompressionType type = CompressionType.parse(field.field(COMPRESSION_TYPE).asString());
        int uncompressedSize = (int) field.field(UNCOMPRESSED_SIZE).asLong();
        return new CompressionInfo(type, uncompressedSize);
    }

    public void serialize(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStringField(COMPRESSION_TYPE, compressionType.name());
        jsonGenerator.writeNumberField(UNCOMPRESSED_SIZE, uncompressedSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CompressionInfo that = (CompressionInfo) o;

        if (uncompressedSize != that.uncompressedSize) return false;
        if (compressionType != that.compressionType) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = compressionType.hashCode();
        result = 31 * result + uncompressedSize;
        return result;
    }
}
