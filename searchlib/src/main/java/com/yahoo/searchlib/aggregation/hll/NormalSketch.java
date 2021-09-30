// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import com.google.common.base.Preconditions;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.util.Arrays;

/**
 * Sketch used by the HyperLogLog (HLL) algorithm.
 *
 * @author bjorncs
 */
public class NormalSketch extends Sketch<NormalSketch>  {

    public static final int classId = registerClass(0x4000 + 170, NormalSketch.class);

    private final byte[] data;
    private final int bucketMask;
    private static final LZ4Factory lz4Factory = LZ4Factory.safeInstance();

    /**
     * Create a sketch with the default precision given by {@link HyperLogLog#DEFAULT_PRECISION}.
     * */
    public NormalSketch() {
        this(HyperLogLog.DEFAULT_PRECISION);
    }

    /**
     * Create a sketch with a given HLL precision parameter.
     *
     * @param precision The precision parameter used by HLL. Determines the size of the sketch.
     */
    public NormalSketch(int precision) {
        this.data = new byte[1 << precision];
        this.bucketMask = (1 << precision) - 1; // A mask where the lowest `precision` bits are 1.
    }

    /**
     * Lossless merge of sketches. Performs a pairwise maximum on the underlying data array.
     *
     * @param other Other sketch
     */
    @Override
    public void merge(NormalSketch other) {
        Preconditions.checkArgument(data.length == other.data.length,
                "Trying to merge sketch with one of different size. Expected %s, actual %s", data.length, other.data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Math.max(data[i], other.data[i]);
        }
    }

    /**
     * Aggregates the hash values.
     *
     * @param hashValues Provides an iterator for the hash values
     */
    @Override
    public void aggregate(Iterable<Integer> hashValues) {
        for (int hash : hashValues) {
            aggregate(hash);
        }
    }

    /**
     * Aggregates the hash value.
     *
     * @param hash Hash value.
     */
    @Override
    public void aggregate(int hash) {
        int existingValue = data[hash & bucketMask];
        int newValue = Integer.numberOfLeadingZeros(hash | bucketMask) + 1;
        data[hash & bucketMask] = (byte) Math.max(newValue, existingValue);
    }

    /**
     * Serializes the Sketch.
     *
     * Serialization format
     * ==================
     * Original size:     4 bytes
     * Compressed size:   4 bytes
     * Compressed data:   N * 1 bytes
     *
     * Invariant:
     *      compressed size &lt;= original size
     *
     * Special case:
     *      compressed size == original size =&gt; data is uncompressed
     *
     * @param buf Serializer
     */
    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, data.length);
        try {
            LZ4Compressor c = lz4Factory.highCompressor();
            byte[] compressedData = new byte[data.length];
            int compressedSize = c.compress(data, compressedData);
            serializeDataArray(compressedData, compressedSize, buf);
        } catch (LZ4Exception e) {
            // LZ4Compressor.compress will throw this exception if it is unable to compress
            // into compressedData (when compressed size >= original size)
            serializeDataArray(data, data.length, buf);
        }
    }

    private static void serializeDataArray(byte[] source, int length, Serializer buf) {
        buf.putInt(null, length);
        for (int i = 0; i < length; i++) {
            buf.putByte(null, source[i]);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int length = buf.getInt(null);
        int compressedLength = buf.getInt(null);
        Preconditions.checkState(length == data.length,
                "Size of serialized sketch does not match expected value. Expected %s, actual %s.", data.length, length);

        if (length == compressedLength) {
            deserializeDataArray(data, length, buf);
        } else {
            LZ4FastDecompressor c = lz4Factory.fastDecompressor();
            byte[] compressedData = buf.getBytes(null, compressedLength);
            c.decompress(compressedData, data);
        }
    }

    private static void deserializeDataArray(byte[] destination, int length, Deserializer buf) {
        for (int i = 0; i < length; i++) {
            destination[i] = buf.getByte(null);
        }
    }

    /**
     * Returns the underlying byte array backing the sketch.
     *
     * @return The underlying sketch data
     */
    public byte[] data() {
        return data;
    }

    /**
     * Sketch size.
     *
     * @return Number of buckets in the sketch.
     */
    public int size() {
        return data.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NormalSketch sketch = (NormalSketch) o;

        if (!Arrays.equals(data, sketch.data)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public String toString() {
        return "NormalSketch{" +
                "data=" + Arrays.toString(data) +
                '}';
    }
}
