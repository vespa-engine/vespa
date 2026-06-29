// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the sequential read operations on {@link BufferedInput} (getByte, eof, skip,
 * getBytes, getConsumedSize) behave identically whether reading a single backing array or a
 * {@link ByteSource} that hands out the input in small chunks.
 */
public class BufferedInputTest {

    /** Hands out the input in fixed-size chunks to force chunk boundaries mid-token. */
    private static final class ChunkedByteSource implements ByteSource {
        private final byte[] data;
        private final int chunk;
        private int pos = 0;
        ChunkedByteSource(byte[] data, int chunk) { this.data = data; this.chunk = chunk; }
        @Override public byte[] next() {
            if (pos >= data.length) return null;
            int n = Math.min(chunk, data.length - pos);
            byte[] b = Arrays.copyOfRange(data, pos, pos + n);
            pos += n;
            return b;
        }
    }

    private static byte[] sequence(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) i;
        return b;
    }

    @Test
    public void getByteAndEofWorkAcrossChunks() {
        byte[] data = sequence(50);
        for (int chunk : new int[] {1, 2, 3, 7, 50, 1000}) {
            BufferedInput in = new BufferedInput(new ChunkedByteSource(data, chunk));
            byte[] got = new byte[data.length];
            int i = 0;
            while ( ! in.eof()) {
                got[i++] = in.getByte();
            }
            assertEquals("chunk " + chunk, data.length, i);
            assertArrayEquals("chunk " + chunk, data, got);
            assertTrue(in.eof());
            assertFalse(in.failed());
        }
    }

    @Test
    public void getBytesWorksAcrossChunks() {
        byte[] data = sequence(50);
        for (int chunk : new int[] {1, 3, 7, 50}) {
            BufferedInput in = new BufferedInput(new ChunkedByteSource(data, chunk));
            assertArrayEquals("chunk " + chunk, Arrays.copyOfRange(data, 0, 10), in.getBytes(10));
            assertArrayEquals("chunk " + chunk, Arrays.copyOfRange(data, 10, 50), in.getBytes(40));
            assertTrue(in.eof());
            assertFalse(in.failed());
        }
    }

    @Test
    public void skipWorksAcrossChunks() {
        byte[] data = sequence(50);
        for (int chunk : new int[] {1, 3, 7, 50}) {
            BufferedInput in = new BufferedInput(new ChunkedByteSource(data, chunk));
            in.skip(20);
            assertArrayEquals("chunk " + chunk, Arrays.copyOfRange(data, 20, 50), in.getBytes(30));
            assertTrue(in.eof());
            assertFalse(in.failed());
        }
    }

    @Test
    public void consumedSizeTracksTotalAcrossChunks() {
        byte[] data = sequence(50);
        BufferedInput in = new BufferedInput(new ChunkedByteSource(data, 3));
        in.getByte();
        in.skip(9);
        in.getBytes(20);
        assertEquals(30, in.getConsumedSize());
    }

    @Test
    public void getBytesUnderflowFails() {
        BufferedInput in = new BufferedInput(new ChunkedByteSource(sequence(5), 2));
        assertArrayEquals(new byte[0], in.getBytes(10));
        assertTrue(in.failed());
        assertEquals(0, in.getConsumedSize());
    }

    /**
     * An IOException from the underlying source while fetching a chunk surfaces as a failed
     * decode (it does not propagate), with the exception text folded into the error message.
     */
    @Test
    public void ioExceptionWhileReadingFailsTheDecode() {
        ByteSource throwing = () -> { throw new IOException("disconnected"); };
        BufferedInput in = new BufferedInput(throwing);
        assertEquals(0, in.getByte());
        assertTrue(in.failed());
        assertTrue("got: " + in.getErrorMessage(), in.getErrorMessage().contains("IO error reading input"));
        assertTrue("got: " + in.getErrorMessage(), in.getErrorMessage().contains("disconnected"));
    }

    /** An IOException raised partway through the stream still surfaces as a failure, not a throw. */
    @Test
    public void ioExceptionMidStreamFailsTheDecode() {
        ByteSource throwing = new ByteSource() {
            private boolean first = true;
            @Override public byte[] next() throws IOException {
                if (first) { first = false; return sequence(4); }
                throw new IOException("disconnected");
            }
        };
        BufferedInput in = new BufferedInput(throwing);
        for (int i = 0; i < 4; i++) assertEquals((byte) i, in.getByte()); // first chunk reads fine
        assertFalse(in.failed());
        in.getByte(); // forces the next fetch, which throws
        assertTrue(in.failed());
        assertTrue("got: " + in.getErrorMessage(), in.getErrorMessage().contains("IO error reading input"));
    }

    /**
     * Check that an empty chunk as input signals EOF.
     */
    @Test
    public void emptyChunksSignalsEOF() {
        byte[] data = sequence(10);
        ByteSource manyEmpties = new ByteSource() {
            private int emitted = 0;
            private int pos = 0;
            @Override public byte[] next() {
                if (emitted++ < 3) {
                    return new byte[] { data[pos++] };
                } else {
                    return new byte[0];
                }
            }
        };
        BufferedInput in = new BufferedInput(manyEmpties);
        byte[] got = new byte[data.length];
        int i = 0;
        while ( ! in.eof()) got[i++] = in.getByte();
        assertEquals(i, 3);
        assertEquals(data[0], got[0]);
        assertEquals(data[1], got[1]);
        assertEquals(data[2], got[2]);
        assertFalse(in.failed());
    }

    @Test
    public void skipUnderflowFails() {
        BufferedInput in = new BufferedInput(new ChunkedByteSource(sequence(5), 2));
        in.skip(10);
        assertTrue(in.failed());
    }

    /**
     * A bogus huge size (as can appear in a corrupt length prefix) must fail with
     * underflow rather than attempt to allocate the array up front and OOM. The tiny
     * input here would never satisfy the request, so allocating Integer.MAX_VALUE
     * bytes would be both wrong and fatal.
     * <p>
     * The position is advanced first so that {@code position + size} would overflow a
     * naive bounds check (wrapping to a negative value that slips past {@code > end}):
     * the check must use subtraction so the bogus size is still rejected.
     */
    @Test
    public void getBytesWithHugeSizeFailsWithoutAllocating() {
        for (int size : new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE - 4}) {
            BufferedInput array = new BufferedInput(sequence(10));
            array.skip(8); // position past zero, so 8 + (MAX-4) overflows int
            assertArrayEquals(new byte[0], array.getBytes(size));
            assertTrue("size " + size, array.failed());

            BufferedInput stream = new BufferedInput(new ChunkedByteSource(sequence(10), 4));
            stream.skip(8);
            assertArrayEquals(new byte[0], stream.getBytes(size));
            assertTrue("size " + size, stream.failed());

            // getBytesView and skip share the same overflow-prone bounds check.
            BufferedInput view = new BufferedInput(sequence(10));
            view.skip(8);
            var got = view.getBytesView(size);
            assertEquals("size " + size, 0, got.size());
            assertTrue("size " + size, view.failed());

            BufferedInput skip = new BufferedInput(sequence(10));
            skip.skip(8);
            skip.skip(size);
            assertTrue("size " + size, skip.failed());
        }
    }

    @Test
    public void getBytesViewWorksWithinAndAcrossChunks() {
        byte[] data = sequence(50);
        for (int chunk : new int[] {1, 3, 7, 50}) {
            BufferedInput in = new BufferedInput(new ChunkedByteSource(data, chunk));
            var first = in.getBytesView(5);
            assertArrayEquals("chunk " + chunk,
                              Arrays.copyOfRange(data, 0, 5),
                              Arrays.copyOfRange(first.data(), first.start(), first.end()));
            var second = in.getBytesView(45);
            assertArrayEquals("chunk " + chunk,
                              Arrays.copyOfRange(data, 5, 50),
                              Arrays.copyOfRange(second.data(), second.start(), second.end()));
            assertTrue(in.eof());
            assertFalse(in.failed());
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] ret = new byte[a.length + b.length];
        System.arraycopy(a, 0, ret, 0, a.length);
        System.arraycopy(b, 0, ret, a.length, b.length);
        return ret;
    }

    /** In byte[] mode the whole input is retained, so getOffending is exactly what was consumed. */
    @Test
    public void getOffendingReturnsConsumedBytesInArrayMode() {
        BufferedInput in = new BufferedInput(sequence(10));
        in.skip(4);
        in.fail("boom");
        assertTrue(in.failed());
        assertEquals("boom", in.getErrorMessage());
        assertArrayEquals(new byte[] {0, 1, 2, 3}, in.getOffending());
    }

    /** getOffending honours the start offset of a windowed byte[] input. */
    @Test
    public void getOffendingRespectsOffsetInArrayMode() {
        BufferedInput in = new BufferedInput(sequence(10), 2, 6); // window over bytes 2..7
        in.skip(3);
        in.fail("x");
        assertArrayEquals(new byte[] {2, 3, 4}, in.getOffending());
    }

    /** When the whole input still fits in the current chunk, nothing is dropped and no marker is added. */
    @Test
    public void getOffendingHasNoMarkerWhenNothingDropped() {
        BufferedInput in = new BufferedInput(new ChunkedByteSource(sequence(10), 100));
        in.skip(4);
        in.fail("boom");
        assertArrayEquals(new byte[] {0, 1, 2, 3}, in.getOffending());
    }

    /**
     * Once earlier chunks have been discarded, getOffending can only report the last two buffers,
     * prefixed by a "[... N bytes ...]" marker accounting for the dropped prefix.
     */
    @Test
    public void getOffendingAcrossChunksIncludesDroppedBytesMarker() {
        BufferedInput in = new BufferedInput(new ChunkedByteSource(sequence(20), 4));
        in.skip(14);
        in.fail("boom");

        // bytes 0..7 fell out of the two retained buffers; bytes 8..13 remain
        byte[] marker = "[... 8 bytes ...]".getBytes(StandardCharsets.UTF_8);
        byte[] expected = concat(marker, new byte[] {8, 9, 10, 11, 12, 13});
        assertArrayEquals(expected, in.getOffending());
    }

    private static Slime streamingBinaryDecode(byte[] bytes, int chunk) {
        BinaryDecoder dec = new BinaryDecoder();
        Slime out = new Slime();
        dec.in = new BufferedInput(new ChunkedByteSource(bytes, chunk));
        BinaryDecoder.decodeSymbolTable(dec.in, out.symbolTable());
        dec.decodeValue(new SlimeInserter(out));
        assertFalse(dec.in.failed());
        return out;
    }

    @Test
    public void binaryDecodeFromChunkedSourceMatchesInMemory() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("name", "a fairly long string value 佳 with some utf8 content");
        root.setLong("count", 1234567);
        root.setDouble("ratio", 3.14159);
        root.setData("blob", sequence(40));
        Cursor arr = root.setArray("items");
        for (int i = 0; i < 30; i++) arr.addString("item-number-" + i);
        byte[] bytes = BinaryFormat.encode(slime);
        assertTrue("input should span several small chunks", bytes.length > 20);

        byte[] reference = BinaryFormat.encode(new BinaryDecoder().decode(bytes));
        for (int chunk : new int[] {1, 2, 3, 7, 13, bytes.length, 100000}) {
            byte[] streamed = BinaryFormat.encode(streamingBinaryDecode(bytes, chunk));
            assertArrayEquals("chunk " + chunk, reference, streamed);
        }
    }
}
