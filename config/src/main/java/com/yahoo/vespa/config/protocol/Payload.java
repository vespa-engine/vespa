// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.LZ4PayloadCompressor;

import java.util.Objects;

/**
 * An immutable config payload
 *
 * @author hmusum
 * @author bratseth
 */
public class Payload {

    private final Utf8Array data;
    private final CompressionInfo compressionInfo;
    private final static LZ4PayloadCompressor compressor = new LZ4PayloadCompressor();

    private Payload(ConfigPayload payload) {
        this.data = payload.toUtf8Array(true);
        this.compressionInfo = CompressionInfo.create(CompressionType.UNCOMPRESSED, data.getByteLength());
    }

    private Payload(Utf8Array payload, CompressionInfo compressionInfo) {
        Objects.requireNonNull(payload, "Payload");
        Objects.requireNonNull(compressionInfo, "CompressionInfo");
        this.data = payload;
        this.compressionInfo = compressionInfo;
    }

    public static Payload from(ConfigPayload payload) {
        return new Payload(payload);
    }

    /** Creates an uncompressed payload from a string */
    public static Payload from(String payload) {
        return new Payload(new Utf8String(payload), CompressionInfo.uncompressed());
    }

    public static Payload from(String payload, CompressionInfo compressionInfo) {
        return new Payload(new Utf8String(payload), compressionInfo);
    }

    /** Creates an uncompressed payload from an Utf8Array */
    public static Payload from(Utf8Array payload) {
        return new Payload(payload, CompressionInfo.uncompressed());
    }

    public static Payload from(Utf8Array payload, CompressionInfo compressionInfo) {
        return new Payload(payload, compressionInfo);
    }

    public Utf8Array getData() { return data; }

    /** Returns a copy of this payload where the data is compressed using the given compression */
    public Payload withCompression(CompressionType requestedCompression) {
        CompressionType responseCompression = compressionInfo.getCompressionType();
        if (requestedCompression == CompressionType.UNCOMPRESSED && responseCompression == CompressionType.LZ4) {
            byte[] buffer = compressor.decompress(data.getBytes(), compressionInfo.getUncompressedSize());
            Utf8Array data = new Utf8Array(buffer);
            CompressionInfo info = CompressionInfo.create(CompressionType.UNCOMPRESSED, compressionInfo.getUncompressedSize());
            return Payload.from(data, info);
        } else if (requestedCompression == CompressionType.LZ4 && responseCompression == CompressionType.UNCOMPRESSED) {
            Utf8Array data = new Utf8Array(compressor.compress(this.data.getBytes()));
            CompressionInfo info = CompressionInfo.create(CompressionType.LZ4, this.data.getByteLength());
            return Payload.from(data, info);
        } else {
            return Payload.from(data, compressionInfo);
        }
    }

    public CompressionInfo getCompressionInfo() { return compressionInfo; }

    @Override
    public String toString() {
        if (compressionInfo.getCompressionType() == CompressionType.UNCOMPRESSED)
            return data.toString();
        else
            return withCompression(CompressionType.UNCOMPRESSED).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Payload other = (Payload) o;
        return this.compressionInfo.equals(other.compressionInfo) && this.data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode() + 31 * compressionInfo.hashCode();
    }

}
