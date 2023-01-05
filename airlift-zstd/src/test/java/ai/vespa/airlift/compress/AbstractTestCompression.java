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
package ai.vespa.airlift.compress;

import com.google.common.primitives.Bytes;
import ai.vespa.airlift.compress.benchmark.DataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.concurrent.ThreadLocalRandom;

import static com.google.common.base.Preconditions.checkPositionIndexes;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractTestCompression
{
    private List<DataSet> testCases = setup();

    protected abstract Compressor getCompressor();

    protected abstract Decompressor getDecompressor();

    protected abstract Compressor getVerifyCompressor();

    protected abstract Decompressor getVerifyDecompressor();

    protected boolean isByteBufferSupported()
    {
        return true;
    }

    private static List<DataSet> setup() {
        List<DataSet> testCases = new ArrayList<>();

        testCases.add(new DataSet("nothing", new byte[0]));
        testCases.add(new DataSet("short literal", "hello world!".getBytes(UTF_8)));
        testCases.add(new DataSet("small copy", "XXXXabcdabcdABCDABCDwxyzwzyz123".getBytes(UTF_8)));
        testCases.add(new DataSet("long copy", "XXXXabcdefgh abcdefgh abcdefgh abcdefgh abcdefgh abcdefgh ABC".getBytes(UTF_8)));

        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        testCases.add(new DataSet("long literal", data));

        testCases.addAll(TestingModule.dataSets());
        return testCases;
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testDecompress(DataSet dataSet)
            throws Exception
    {
        byte[] uncompressedOriginal = dataSet.getUncompressed();
        byte[] compressed = prepareCompressedData(uncompressedOriginal);

        byte[] uncompressed = new byte[uncompressedOriginal.length];

        Decompressor decompressor = getDecompressor();
        int uncompressedSize = decompressor.decompress(
                compressed,
                0,
                compressed.length,
                uncompressed,
                0,
                uncompressed.length);

        assertByteArraysEqual(uncompressed, 0, uncompressedSize, uncompressedOriginal, 0, uncompressedOriginal.length);
    }

    // Tests that decompression works correctly when the decompressed data does not span the entire output buffer
    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testDecompressWithOutputPadding(DataSet dataSet)
    {
        int padding = 1021;

        byte[] uncompressedOriginal = dataSet.getUncompressed();
        byte[] compressed = prepareCompressedData(uncompressedOriginal);

        byte[] uncompressed = new byte[uncompressedOriginal.length + 2 * padding]; // pre + post padding

        Decompressor decompressor = getDecompressor();
        int uncompressedSize = decompressor.decompress(
                compressed,
                0,
                compressed.length,
                uncompressed,
                padding,
                uncompressedOriginal.length + padding);

        assertByteArraysEqual(uncompressed, padding, uncompressedSize, uncompressedOriginal, 0, uncompressedOriginal.length);
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testDecompressionBufferOverrun(DataSet dataSet)
    {
        byte[] uncompressedOriginal = dataSet.getUncompressed();
        byte[] compressed = prepareCompressedData(uncompressedOriginal);

        // add padding with random bytes that we can verify later
        byte[] padding = new byte[100];
        ThreadLocalRandom.current().nextBytes(padding);

        byte[] uncompressed = Bytes.concat(new byte[uncompressedOriginal.length], padding);

        Decompressor decompressor = getDecompressor();
        int uncompressedSize = decompressor.decompress(
                compressed,
                0,
                compressed.length,
                uncompressed,
                0,
                uncompressedOriginal.length);

        assertByteArraysEqual(uncompressed, 0, uncompressedSize, uncompressedOriginal, 0, uncompressedOriginal.length);

        // verify padding is intact
        assertByteArraysEqual(padding, 0, padding.length, uncompressed, uncompressed.length - padding.length, padding.length);
    }

    @Test
    public void testDecompressInputBoundsChecks()
    {
        byte[] data = new byte[1024];
        new Random(1234).nextBytes(data);
        Compressor compressor = getCompressor();
        byte[] compressed = new byte[compressor.maxCompressedLength(data.length)];
        int compressedLength = compressor.compress(data, 0, data.length, compressed, 0, compressed.length);

        Decompressor decompressor = getDecompressor();
        Throwable throwable;

        // null input buffer
        assertThatThrownBy(() -> decompressor.decompress(null, 0, compressedLength, data, 0, data.length))
                .isInstanceOf(NullPointerException.class);

        // mis-declared buffer size
        byte[] compressedChoppedOff = Arrays.copyOf(compressed, compressedLength - 1);
        throwable = catchThrowable(() -> decompressor.decompress(compressedChoppedOff, 0, compressedLength, data, 0, data.length));
        if (throwable instanceof UncheckedIOException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }

        // overrun because of offset
        byte[] compressedWithPadding = new byte[10 + compressedLength - 1];
        arraycopy(compressed, 0, compressedWithPadding, 10, compressedLength - 1);

        throwable = catchThrowable(() -> decompressor.decompress(compressedWithPadding, 10, compressedLength, data, 0, data.length));
        if (throwable instanceof UncheckedIOException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }
    }

    @Test
    public void testDecompressOutputBoundsChecks()
    {
        byte[] data = new byte[1024];
        new Random(1234).nextBytes(data);
        Compressor compressor = getCompressor();
        byte[] compressed = new byte[compressor.maxCompressedLength(data.length)];
        int compressedLength = compressor.compress(data, 0, data.length, compressed, 0, compressed.length);
        byte[] input = Arrays.copyOf(compressed, compressedLength);

        Decompressor decompressor = getDecompressor();
        Throwable throwable;

        // null output buffer
        assertThatThrownBy(() -> decompressor.decompress(input, 0, input.length, null, 0, data.length))
                .isInstanceOf(NullPointerException.class);

        // small buffer
        assertThatThrownBy(() -> decompressor.decompress(input, 0, input.length, new byte[1], 0, 1))
                .hasMessageMatching("All input was not consumed|attempt to write.* outside of destination buffer.*|Malformed input.*|Uncompressed length 1024 must be less than 1|Output buffer too small.*");

        // mis-declared buffer size
        throwable = catchThrowable(() -> decompressor.decompress(input, 0, input.length, new byte[1], 0, data.length));
        if (throwable instanceof IndexOutOfBoundsException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }

        // mis-declared buffer size with greater buffer
        throwable = catchThrowable(() -> decompressor.decompress(input, 0, input.length, new byte[data.length - 1], 0, data.length));
        if (throwable instanceof IndexOutOfBoundsException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testDecompressByteBufferHeapToHeap(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        ByteBuffer compressed = ByteBuffer.wrap(prepareCompressedData(uncompressedOriginal));
        ByteBuffer uncompressed = ByteBuffer.allocate(uncompressedOriginal.length);

        getDecompressor().decompress(compressed, uncompressed);
        ((Buffer) uncompressed).flip();

        assertByteBufferEqual(ByteBuffer.wrap(uncompressedOriginal), uncompressed);
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testDecompressByteBufferHeapToDirect(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        ByteBuffer compressed = ByteBuffer.wrap(prepareCompressedData(uncompressedOriginal));
        ByteBuffer uncompressed = ByteBuffer.allocateDirect(uncompressedOriginal.length);

        getDecompressor().decompress(compressed, uncompressed);
        ((Buffer) uncompressed).flip();

        assertByteBufferEqual(ByteBuffer.wrap(uncompressedOriginal), uncompressed);
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testDecompressByteBufferDirectToHeap(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        ByteBuffer compressed = toDirectBuffer(prepareCompressedData(uncompressedOriginal));
        ByteBuffer uncompressed = ByteBuffer.allocate(uncompressedOriginal.length);

        getDecompressor().decompress(compressed, uncompressed);
        ((Buffer) uncompressed).flip();

        assertByteBufferEqual(ByteBuffer.wrap(uncompressedOriginal), uncompressed);
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testDecompressByteBufferDirectToDirect(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        ByteBuffer compressed = toDirectBuffer(prepareCompressedData(uncompressedOriginal));
        ByteBuffer uncompressed = ByteBuffer.allocateDirect(uncompressedOriginal.length);

        getDecompressor().decompress(compressed, uncompressed);
        ((Buffer) uncompressed).flip();

        assertByteBufferEqual(ByteBuffer.wrap(uncompressedOriginal), uncompressed);
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testCompress(DataSet testCase)
            throws Exception
    {
        Compressor compressor = getCompressor();

        byte[] originalUncompressed = testCase.getUncompressed();
        byte[] compressed = new byte[compressor.maxCompressedLength(originalUncompressed.length)];

        // attempt to compress slightly different data to ensure the compressor doesn't keep state
        // between calls that may affect results
        if (originalUncompressed.length > 1) {
            byte[] output = new byte[compressor.maxCompressedLength(originalUncompressed.length - 1)];
            compressor.compress(originalUncompressed, 1, originalUncompressed.length - 1, output, 0, output.length);
        }

        int compressedLength = compressor.compress(
                originalUncompressed,
                0,
                originalUncompressed.length,
                compressed,
                0,
                compressed.length);

        verifyCompressedData(originalUncompressed, compressed, compressedLength);
    }

    @Test
    public void testCompressInputBoundsChecks()
    {
        Compressor compressor = getCompressor();
        int declaredInputLength = 1024;
        int maxCompressedLength = compressor.maxCompressedLength(1024);
        byte[] output = new byte[maxCompressedLength];
        Throwable throwable;

        // null input buffer
        assertThatThrownBy(() -> compressor.compress(null, 0, declaredInputLength, output, 0, output.length))
                .isInstanceOf(NullPointerException.class);

        // mis-declared buffer size
        throwable = catchThrowable(() -> compressor.compress(new byte[1], 0, declaredInputLength, output, 0, output.length));
        if (throwable instanceof IndexOutOfBoundsException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }

        // max too small
        throwable = catchThrowable(() -> compressor.compress(new byte[declaredInputLength - 1], 0, declaredInputLength, output, 0, output.length));
        if (throwable instanceof IndexOutOfBoundsException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }

        // overrun because of offset
        throwable = catchThrowable(() -> compressor.compress(new byte[declaredInputLength + 10], 11, declaredInputLength, output, 0, output.length));
        if (throwable instanceof IndexOutOfBoundsException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }
    }

    @Test
    public void testCompressOutputBoundsChecks()
    {
        Compressor compressor = getCompressor();
        int minCompressionOverhead = compressor.maxCompressedLength(0);
        byte[] input = new byte[minCompressionOverhead * 4 + 1024];
        new Random(1234).nextBytes(input);
        int maxCompressedLength = compressor.maxCompressedLength(input.length);
        Throwable throwable;

        // null output buffer
        assertThatThrownBy(() -> compressor.compress(input, 0, input.length, null, 0, maxCompressedLength))
                .isInstanceOf(NullPointerException.class);

        // small buffer
        assertThatThrownBy(() -> compressor.compress(input, 0, input.length, new byte[1], 0, 1))
                .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*|Max output length must be larger than .*|Output buffer must be at least.*|Output buffer too small");

        // mis-declared buffer size
        throwable = catchThrowable(() -> compressor.compress(input, 0, input.length, new byte[1], 0, maxCompressedLength));
        if (throwable instanceof ArrayIndexOutOfBoundsException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }

        // mis-declared buffer size with buffer large enough to hold compression frame header (if any)
        throwable = catchThrowable(() -> compressor.compress(input, 0, input.length, new byte[minCompressionOverhead * 2], 0, maxCompressedLength));
        if (throwable instanceof ArrayIndexOutOfBoundsException) {
            // OK
        }
        else {
            assertThat(throwable)
                    .hasMessageMatching(".*must not be greater than size.*|Invalid offset or length.*");
        }
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testCompressByteBufferHeapToHeap(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        Compressor compressor = getCompressor();

        verifyCompressByteBuffer(
                compressor,
                ByteBuffer.wrap(uncompressedOriginal),
                ByteBuffer.allocate(compressor.maxCompressedLength(uncompressedOriginal.length)));
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testCompressByteBufferHeapToDirect(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());
        if (!isByteBufferSupported()) {
            return; // throw new SkipException("ByteBuffer not supported");
        }

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        Compressor compressor = getCompressor();

        verifyCompressByteBuffer(
                compressor,
                ByteBuffer.wrap(uncompressedOriginal),
                ByteBuffer.allocateDirect(compressor.maxCompressedLength(uncompressedOriginal.length)));
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testCompressByteBufferDirectToHeap(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        Compressor compressor = getCompressor();

        verifyCompressByteBuffer(
                compressor,
                toDirectBuffer(uncompressedOriginal),
                ByteBuffer.allocate(compressor.maxCompressedLength(uncompressedOriginal.length)));
    }

    @ParameterizedTest
    @MethodSource("getAllDataSets")
    public void testCompressByteBufferDirectToDirect(DataSet dataSet)
            throws Exception
    {
        assumeTrue(isByteBufferSupported());

        byte[] uncompressedOriginal = dataSet.getUncompressed();

        Compressor compressor = getCompressor();

        verifyCompressByteBuffer(
                compressor,
                toDirectBuffer(uncompressedOriginal),
                ByteBuffer.allocateDirect(compressor.maxCompressedLength(uncompressedOriginal.length)));
    }

    private void verifyCompressByteBuffer(Compressor compressor, ByteBuffer expected, ByteBuffer compressed)
    {
        // attempt to compress slightly different data to ensure the compressor doesn't keep state
        // between calls that may affect results
        if (expected.remaining() > 1) {
            ByteBuffer duplicate = expected.duplicate();
            duplicate.get(); // skip one byte
            compressor.compress(duplicate, ByteBuffer.allocate(((Buffer) compressed).remaining()));
        }

        compressor.compress(expected.duplicate(), compressed);
        ((Buffer) compressed).flip();

        ByteBuffer uncompressed = ByteBuffer.allocate(((Buffer) expected).remaining());

        // TODO: validate with "control" decompressor
        getDecompressor().decompress(compressed, uncompressed);
        ((Buffer) uncompressed).flip();

        assertByteBufferEqual(expected.duplicate(), uncompressed);
    }

    private void verifyCompressedData(byte[] originalUncompressed, byte[] compressed, int compressedLength)
    {
        byte[] uncompressed = new byte[originalUncompressed.length];
        int uncompressedSize = getVerifyDecompressor().decompress(compressed, 0, compressedLength, uncompressed, 0, uncompressed.length);

        assertByteArraysEqual(uncompressed, 0, uncompressedSize, originalUncompressed, 0, originalUncompressed.length);
    }

    @Test
    public void testRoundTripSmallLiteral()
            throws Exception
    {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        Compressor compressor = getCompressor();
        byte[] compressed = new byte[compressor.maxCompressedLength(data.length)];
        byte[] uncompressed = new byte[data.length];

        for (int i = 1; i < data.length; i++) {
            try {
                int written = compressor.compress(
                        data,
                        0,
                        i,
                        compressed,
                        0,
                        compressed.length);

                int decompressedSize = getDecompressor().decompress(compressed, 0, written, uncompressed, 0, uncompressed.length);

                assertByteArraysEqual(data, 0, i, uncompressed, 0, decompressedSize);
                assertEquals(decompressedSize, i);
            }
            catch (MalformedInputException e) {
                throw new RuntimeException("Failed with " + i + " bytes of input", e);
            }
        }
    }

    public Stream<DataSet> getAllDataSets() {
        return testCases.stream();
    }

    public static void assertByteArraysEqual(byte[] left, int leftOffset, int leftLength, byte[] right, int rightOffset, int rightLength)
    {
        checkPositionIndexes(leftOffset, leftOffset + leftLength, left.length);
        checkPositionIndexes(rightOffset, rightOffset + rightLength, right.length);

        for (int i = 0; i < Math.min(leftLength, rightLength); i++) {
            if (left[leftOffset + i] != right[rightOffset + i]) {
                fail(String.format("Byte arrays differ at position %s: 0x%02X vs 0x%02X", i, left[leftOffset + i], right[rightOffset + i]));
            }
        }

        assertEquals(leftLength, rightLength, String.format("Array lengths differ: %s vs %s", leftLength, rightLength));
    }

    private static void assertByteBufferEqual(ByteBuffer left, ByteBuffer right)
    {
        Buffer leftBuffer = left;
        Buffer rightBuffer = right;

        int leftPosition = leftBuffer.position();
        int rightPosition = rightBuffer.position();
        for (int i = 0; i < Math.min(leftBuffer.remaining(), rightBuffer.remaining()); i++) {
            if (left.get(leftPosition + i) != right.get(rightPosition + i)) {
                fail(String.format("Byte buffers differ at position %s: 0x%02X vs 0x%02X", i, left.get(leftPosition + i), right.get(rightPosition + i)));
            }
        }

        assertEquals(leftBuffer.remaining(), rightBuffer.remaining(), String.format("Buffer lengths differ: %s vs %s", leftBuffer.remaining(), leftBuffer.remaining()));
    }

    private static ByteBuffer toDirectBuffer(byte[] data)
    {
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data);

        ((Buffer) direct).flip();

        return direct;
    }

    private byte[] prepareCompressedData(byte[] uncompressed)
    {
        Compressor compressor = getVerifyCompressor();

        byte[] compressed = new byte[compressor.maxCompressedLength(uncompressed.length)];

        int compressedLength = compressor.compress(
                uncompressed,
                0,
                uncompressed.length,
                compressed,
                0,
                compressed.length);

        return Arrays.copyOf(compressed, compressedLength);
    }
}
