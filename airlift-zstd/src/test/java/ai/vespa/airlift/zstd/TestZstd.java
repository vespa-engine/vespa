/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.vespa.airlift.zstd;

import ai.vespa.airlift.compress.AbstractTestCompression;
import ai.vespa.airlift.compress.Compressor;
import ai.vespa.airlift.compress.Decompressor;
import ai.vespa.airlift.compress.MalformedInputException;
import ai.vespa.airlift.compress.benchmark.DataSet;
import ai.vespa.airlift.compress.thirdparty.ZstdJniCompressor;
import ai.vespa.airlift.compress.thirdparty.ZstdJniDecompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestZstd
        extends AbstractTestCompression
{
    @Override
    protected Compressor getCompressor()
    {
        return new ZstdCompressor();
    }

    @Override
    protected Decompressor getDecompressor()
    {
        return new ZstdDecompressor();
    }

    @Override
    protected Compressor getVerifyCompressor()
    {
        return new ZstdJniCompressor(3);
    }

    @Override
    protected Decompressor getVerifyDecompressor()
    {
        return new ZstdJniDecompressor();
    }

    // Ideally, this should be covered by super.testDecompressWithOutputPadding(...), but the data written by the native
    // compressor doesn't include checksums, so it's not a comprehensive test. The dataset for this test has a checksum.
    @Test
    public void testDecompressWithOutputPaddingAndChecksum()
            throws IOException
    {
        int padding = 1021;

        byte[] compressed = getResourceBytes("data/zstd/with-checksum.zst");
        byte[] uncompressed = getResourceBytes("data/zstd/with-checksum");

        byte[] output = new byte[uncompressed.length + padding * 2]; // pre + post padding
        int decompressedSize = getDecompressor().decompress(compressed, 0, compressed.length, output, padding, output.length - padding);

        assertByteArraysEqual(uncompressed, 0, uncompressed.length, output, padding, decompressedSize);
    }

    @Test
    public void testConcatenatedFrames()
            throws IOException
    {
        byte[] compressed = getResourceBytes("data/zstd/multiple-frames.zst");
        byte[] uncompressed = getResourceBytes("data/zstd/multiple-frames");

        byte[] output = new byte[uncompressed.length];
        getDecompressor().decompress(compressed, 0, compressed.length, output, 0, output.length);

        assertByteArraysEqual(uncompressed, 0, uncompressed.length, output, 0, output.length);
    }

    @Test
    public void testInvalidSequenceOffset()
            throws IOException
    {
        byte[] compressed = getResourceBytes("data/zstd/offset-before-start.zst");
        byte[] output = new byte[compressed.length * 10];

        assertThatThrownBy(() -> getDecompressor().decompress(compressed, 0, compressed.length, output, 0, output.length))
                .isInstanceOf(MalformedInputException.class)
                .hasMessageStartingWith("Input is corrupted: offset=894");
    }

    @Test
    public void testSmallLiteralsAfterIncompressibleLiterals()
            throws IOException
    {
        // Ensure the compressor doesn't try to reuse a huffman table that was created speculatively for a previous block
        // which ended up emitting raw literals due to insufficient gain
        Compressor compressor = getCompressor();

        byte[] original = getResourceBytes("data/zstd/small-literals-after-incompressible-literals");
        int maxCompressLength = compressor.maxCompressedLength(original.length);

        byte[] compressed = new byte[maxCompressLength];
        int compressedSize = compressor.compress(original, 0, original.length, compressed, 0, compressed.length);

        byte[] decompressed = new byte[original.length];
        int decompressedSize = getDecompressor().decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

        assertByteArraysEqual(original, 0, original.length, decompressed, 0, decompressedSize);
    }

    @Test
    public void testLargeRle()
            throws IOException
    {
        // Dataset that produces an RLE block with 3-byte header

        Compressor compressor = getCompressor();

        byte[] original = getResourceBytes("data/zstd/large-rle");
        int maxCompressLength = compressor.maxCompressedLength(original.length);

        byte[] compressed = new byte[maxCompressLength];
        int compressedSize = compressor.compress(original, 0, original.length, compressed, 0, compressed.length);

        byte[] decompressed = new byte[original.length];
        int decompressedSize = getDecompressor().decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

        assertByteArraysEqual(original, 0, original.length, decompressed, 0, decompressedSize);
    }

    @Test
    public void testIncompressibleData()
            throws IOException
    {
        // Incompressible data that would require more than maxCompressedLength(...) to store

        Compressor compressor = getCompressor();

        byte[] original = getResourceBytes("data/zstd/incompressible");
        int maxCompressLength = compressor.maxCompressedLength(original.length);

        byte[] compressed = new byte[maxCompressLength];
        int compressedSize = compressor.compress(original, 0, original.length, compressed, 0, compressed.length);

        byte[] decompressed = new byte[original.length];
        int decompressedSize = getDecompressor().decompress(compressed, 0, compressedSize, decompressed, 0, decompressed.length);

        assertByteArraysEqual(original, 0, original.length, decompressed, 0, decompressedSize);
    }

    @Test
    public void testMaxCompressedSize()
    {
        assertEquals(new ZstdCompressor().maxCompressedLength(0), 64);
        assertEquals(new ZstdCompressor().maxCompressedLength(64 * 1024), 65_824);
        assertEquals(new ZstdCompressor().maxCompressedLength(128 * 1024), 131_584);
        assertEquals(new ZstdCompressor().maxCompressedLength(128 * 1024 + 1), 131_585);
    }

    // test over data sets, should the result depend on input size or its compressibility
    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testGetDecompressedSize(DataSet dataSet)
    {
        Compressor compressor = getCompressor();
        byte[] originalUncompressed = dataSet.getUncompressed();
        byte[] compressed = new byte[compressor.maxCompressedLength(originalUncompressed.length)];

        int compressedLength = compressor.compress(originalUncompressed, 0, originalUncompressed.length, compressed, 0, compressed.length);

        assertEquals(ZstdDecompressor.getDecompressedSize(compressed, 0, compressedLength), originalUncompressed.length);

        int padding = 10;
        byte[] compressedWithPadding = new byte[compressedLength + padding];
        Arrays.fill(compressedWithPadding, (byte) 42);
        System.arraycopy(compressed, 0, compressedWithPadding, padding, compressedLength);
        assertEquals(ZstdDecompressor.getDecompressedSize(compressedWithPadding, padding, compressedLength), originalUncompressed.length);
    }

    @Test
    public void testVerifyMagicInAllFrames()
            throws IOException
    {
        Compressor compressor = getCompressor();
        byte[] compressed = getResourceBytes("data/zstd/bad-second-frame.zst");
        byte[] uncompressed = getResourceBytes("data/zstd/multiple-frames");
        byte[] output = new byte[uncompressed.length];
        assertThatThrownBy(() -> getDecompressor().decompress(compressed, 0, compressed.length, output, 0, output.length))
                .isInstanceOf(MalformedInputException.class)
                .hasMessageStartingWith("Invalid magic prefix");
    }
}
