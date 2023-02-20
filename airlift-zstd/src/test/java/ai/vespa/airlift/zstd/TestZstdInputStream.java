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
import ai.vespa.airlift.compress.thirdparty.ZstdJniCompressor;
import ai.vespa.airlift.compress.thirdparty.ZstdJniDecompressor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestZstdInputStream
        extends AbstractTestCompression
{
    static class ByteBufferBackedInputStream
            extends InputStream
    {
        ByteBuffer buf;

        public ByteBufferBackedInputStream(ByteBuffer buf)
        {
            this.buf = buf;
        }

        public int read()
        {
            if (!buf.hasRemaining()) {
                return -1;
            }
            return buf.get() & 0xFF;
        }

        public int read(byte[] bytes, int off, int len)
        {
            if (!buf.hasRemaining()) {
                return -1;
            }
            len = Math.min(len, buf.remaining());
            if (buf.position() < 1) {
                len = 1;
            }
            buf.get(bytes, off, len);
            return len;
        }
    }

    static class WrapDecompressor
            implements Decompressor
    {
        public int decompress(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset, int maxOutputLength)
                throws MalformedInputException
        {
            verifyRange(input, inputOffset, inputLength);
            verifyRange(output, outputOffset, maxOutputLength);
            try {
                int res = 0;
                ByteArrayInputStream ba = new ByteArrayInputStream(input, inputOffset, inputLength);
                InputStream zin = new ZstdInputStream(ba);
                while (res < maxOutputLength) {
                    int len = zin.read(output, outputOffset, maxOutputLength - res);
                    if (len == -1) {
                        return res;
                    }
                    res += len;
                    outputOffset += len;
                }
                if (zin.read() != -1) {
                    throw new RuntimeException("All input was not consumed");
                }
                return res;
            }
            catch (IOException e) {
                throw new RuntimeException("bad io", e);
            }
        }

        public void decompress(ByteBuffer input, ByteBuffer output)
                throws MalformedInputException
        {
            try {
                byte[] tmp = new byte[output.remaining()];
                ByteBufferBackedInputStream bb = new ByteBufferBackedInputStream(input);
                InputStream zin = new ZstdInputStream(bb);
                while (output.position() < output.limit()) {
                    int len = zin.read(tmp);
                    if (len == -1) {
                        return;
                    }
                    output.put(tmp, 0, len);
                }
            }
            catch (IOException ignored) {
            }
        }

        private static void verifyRange(byte[] data, int offset, int length)
        {
            if (offset < 0 || length < 0 || offset + length > data.length) {
                throw new IllegalArgumentException("Invalid offset or length");
            }
        }
    }

    @Override
    protected Compressor getCompressor()
    {
        return new ZstdCompressor();
    }

    @Override
    protected Decompressor getDecompressor()
    {
        return new WrapDecompressor();
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
