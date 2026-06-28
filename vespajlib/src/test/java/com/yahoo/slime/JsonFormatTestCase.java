// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.text.Utf8;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonFormatTestCase {

    @Test
    public void testBasic() {
        System.out.println("test encoding slime holding a single basic value");
        {
            Slime slime = new Slime();
            slime.setBool(false);
            verifyEncoding(slime, "false");
        }

        {
            Slime slime = new Slime();
            slime.setBool(true);
            verifyEncoding(slime, "true");
        }

        {
            Slime slime = new Slime();
            slime.setLong(0);
            verifyEncoding(slime, "0");
        }
        {
            Slime slime = new Slime();
            slime.setLong(13);
            verifyEncoding(slime, "13");
        }
        {
            Slime slime = new Slime();
            slime.setLong(-123456789);
            verifyEncoding(slime, "-123456789");
        }
        {
            Slime slime = new Slime();
            slime.setDouble(0.0);
            verifyEncoding(slime, "0.0");
        }
        {
            Slime slime = new Slime();
            slime.setDouble(1.5);
            verifyEncoding(slime, "1.5");
        }
        {
            Slime slime = new Slime();
            slime.setString("");
            verifyEncoding(slime, "\"\"");
        }
        {
            Slime slime = new Slime();
            slime.setString("fo");
            verifyEncoding(slime, "\"fo\"");
        }
        {
            Slime slime = new Slime();
            slime.setString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
            verifyEncoding(slime, "\"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ\"");
        }
        {
            Slime slime = new Slime();
            slime.setData(new byte[0]);
            verifyEncoding(slime, "\"0x\"");
        }

        {
            Slime slime = new Slime();
            byte[] data = { 42, -123 };
            slime.setData(data);
            String expect = "\"0x2A85\"";
            verifyEncoding(slime, expect);
        }

        {
            Slime slime = new Slime();
            String expected = "\"my\\nencoded\\rsting\\\\is\\bthe\\fnicest\\t\\\"string\\\"\\u0005\"";
            slime.setString("my\nencoded\rsting\\is\bthe\fnicest\t\"string\"" + Character.toString((char) 5));
            verifyEncoding(slime, expected);
        }

        {
            Slime slime = new Slime();
            slime.setDouble(Double.NaN);
            verifyEncoding(slime, "null");
            slime.setDouble(Double.NEGATIVE_INFINITY);
            verifyEncoding(slime, "null");
            slime.setDouble(Double.POSITIVE_INFINITY);
            verifyEncoding(slime, "null");
        }
    }

    @Test
    public void testNullJavaStringNixFallback() {
        Slime slime = new Slime();
        String str = null;
        slime.setString(str);
        assertEquals(Type.NIX, slime.get().type());
        verifyEncoding(slime, "null");
    }

    @Test
    public void testNullUtf8StringNixFallback() {
        Slime slime = new Slime();
        byte[] utf8 = null;
        slime.setString(utf8);
        assertEquals(Type.NIX, slime.get().type());
        verifyEncoding(slime, "null");
    }

    @Test
    public void testNullDataNixFallback() {
        Slime slime = new Slime();
        slime.setData(null);
        assertEquals(Type.NIX, slime.get().type());
        verifyEncoding(slime, "null");
    }

    @Test
    public void testArray() {
        System.out.println("test encoding slime holding an array of various basic values");
        Slime slime = new Slime();
        Cursor c = slime.setArray();
        byte[] data = { 'd', 'a', 't', 'a' };
        c.addNix();
        c.addBool(true);
        c.addLong(42);
        c.addDouble(3.5);
        c.addString("string");
        c.addData(data);

        verifyEncoding(slime, "[null,true,42,3.5,\"string\",\"0x64617461\"]");
    }

    @Test
    public void testObject() {
        System.out.println("test encoding slime holding an object of various basic values");
        Slime slime = new Slime();
        Cursor c = slime.setObject();
        byte[] data = { 'd', 'a', 't', 'a' };
        c.setNix("a");
        c.setBool("b", true);
        c.setLong("c", 42);
        c.setDouble("d", 3.5);
        c.setString("e", "string");
        c.setData("f", data);
        verifyEncoding(slime, "{\"a\":null,\"b\":true,\"c\":42,\"d\":3.5,\"e\":\"string\",\"f\":\"0x64617461\"}");
        String expected = "{\n"
                          + "  \"a\": null,\n"
                          + "  \"b\": true,\n"
                          + "  \"c\": 42,\n"
                          + "  \"d\": 3.5,\n"
                          + "  \"e\": \"string\",\n"
                          + "  \"f\": \"0x64617461\"\n"
                          + "}\n";
        verifyEncoding(slime, expected, false);
    }

    @Test
    public void testNesting() {
        System.out.println("test encoding slime holding a more complex structure");
        Slime slime = new Slime();
        Cursor c1 = slime.setObject();
        c1.setLong("bar", 10);
        Cursor c2 = c1.setArray("foo");
        c2.addLong(20);
        Cursor c3 = c2.addObject();
        c3.setLong("answer", 42);
        verifyEncoding(slime, "{\"bar\":10,\"foo\":[20,{\"answer\":42}]}");
    }

    @Test
    public void testDecodeEncode() {
        System.out.println("test decoding and encoding a json string yields the same string");
        verifyEncodeDecode("{\"bar\":10,\"foo\":[20,{\"answer\":42}]}", true);
        String expected = "{\n"
                          + "  \"a\": null,\n"
                          + "  \"b\": true,\n"
                          + "  \"c\": 42,\n"
                          + "  \"d\": 3.5,\n"
                          + "  \"e\": \"string\",\n"
                          + "  \"f\": \"0x64617461\"\n"
                          + "}\n";
        verifyEncodeDecode(expected, false);
    }

    @Test
    public void testDecodeUnicodeAmp() {
        final String json = "{\"body\":\"some text\\u0026more text\"}";
        Slime slime = new Slime();
        new JsonDecoder().decode(slime, Utf8.toBytesStd(json));
        Cursor a = slime.get().field("body");
        assertEquals("some text&more text", a.asString());
    }

    @Test
    public void testDecodeEncodeUtf8() {
        final String json = "{\n" +
                "  \"rules\": \"# Use unicode equivalents in java source:\\n" +
                "        #\\n" +
                "        #   佳:\u4f73\"\n" +
                "}\n";
        verifyEncodeDecode(json, false);
    }

    @Test
    public void testDecodeUtf8() {
        final String str = "\u4f73:\u4f73";
        final String json = " {\n" +
                "            \"rules\": \"" + str + "\"\n" +
                "        }\n";

        Slime slime = new Slime();
        slime = new JsonDecoder().decode(slime, Utf8.toBytesStd(json));
        Cursor a = slime.get().field("rules");
        assertEquals(str, a.asString());
    }

    private void verifyEncoding(Slime slime, String expected) {
        verifyEncoding(slime, expected, true);
    }

    /** An InputStream that hands out at most maxChunk bytes per read, to force chunk boundaries. */
    private static class ChunkedInputStream extends InputStream {
        private final byte[] data;
        private final int maxChunk;
        private int pos = 0;
        ChunkedInputStream(byte[] data, int maxChunk) { this.data = data; this.maxChunk = maxChunk; }
        @Override public int read() { return pos < data.length ? data[pos++] & 0xff : -1; }
        @Override public int read(byte[] b, int off, int len) {
            if (pos >= data.length) return -1;
            int n = Math.min(Math.min(len, maxChunk), data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }

    /** Delivers a few valid bytes, then fails the read, to exercise the IOException-while-streaming path. */
    private static class FailingInputStream extends InputStream {
        private final byte[] prefix;
        private int pos = 0;
        FailingInputStream(byte[] prefix) { this.prefix = prefix; }
        @Override public int read() throws IOException {
            if (pos < prefix.length) return prefix[pos++] & 0xff;
            throw new IOException("connection reset");
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (pos < prefix.length) {
                int n = Math.min(len, prefix.length - pos);
                System.arraycopy(prefix, pos, b, off, n);
                pos += n;
                return n;
            }
            throw new IOException("connection reset");
        }
    }

    /**
     * An IOException while reading the stream does not propagate: it is turned into a failed
     * decode and surfaces as a partial_result Slime with an error_message, the same shape the
     * other jsonToSlime overloads produce for invalid JSON.
     */
    @Test
    public void testStreamingDecodeIoErrorBecomesPartialResult() {
        byte[] validPrefix = Utf8.toBytesStd("{\"a\": 1, ");
        Slime slime = SlimeUtils.jsonToSlime(new FailingInputStream(validPrefix));
        String error = slime.get().field("error_message").asString();
        assertTrue("expected a non-empty error message", error.length() > 0);
        assertTrue("expected IO error in: " + error, error.contains("IO error reading input"));
        assertTrue("expected cause in: " + error, error.contains("connection reset"));
    }

    private void verifyStreamingDecode(String json, int maxChunk) {
        byte[] bytes = Utf8.toBytesStd(json);
        Slime fromBytes = SlimeUtils.jsonToSlime(bytes);
        Slime fromStream = SlimeUtils.jsonToSlime(new ChunkedInputStream(bytes, maxChunk));
        ByteArrayOutputStream a = new ByteArrayOutputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try {
            new JsonFormat(true).encode(a, fromBytes);
            new JsonFormat(true).encode(b, fromStream);
        } catch (IOException e) {
            fail(e.getMessage());
        }
        assertArrayEquals("chunk size " + maxChunk, a.toByteArray(), b.toByteArray());
    }

    @Test
    public void testStreamingDecodeAcrossChunkBoundaries() {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 2000; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"key\":\"value佳").append(i).append("\",\"n\":").append(i).append("}");
        }
        sb.append("]}");
        String json = sb.toString();
        assertTrue("input must span multiple 8K read buffers", Utf8.toBytesStd(json).length > 8192);

        // tokens, strings and numbers all straddle boundaries at these chunk sizes
        verifyStreamingDecode(json, 1);
        verifyStreamingDecode(json, 3);
        verifyStreamingDecode(json, 8192);
        verifyStreamingDecode(json, Integer.MAX_VALUE);
    }

    @Test
    public void testStreamingDecodeError() {
        String json = "{\"a\": 1 \"b\": 2}"; // missing comma
        byte[] bytes = Utf8.toBytesStd(json);
        Slime fromBytes = SlimeUtils.jsonToSlime(bytes);
        Slime fromStream = SlimeUtils.jsonToSlime(new ByteArrayInputStream(bytes));

        // The whole input fits in a single read buffer, so no bytes are dropped and the streaming
        // path reports the failure identically to the in-memory path.
        assertTrue(fromBytes.get().field("error_message").asString().length() > 0);
        assertEquals(fromBytes.get().field("error_message").asString(),
                     fromStream.get().field("error_message").asString());
        assertArrayEquals(fromBytes.get().field("offending_input").asData(),
                          fromStream.get().field("offending_input").asData());
    }

    @Test
    public void testStreamingDecodeErrorAcrossChunkBoundaries() {
        // A long valid prefix forces the failure to occur many chunks into the stream, so the
        // offending input cannot be reconstructed from a single buffer.
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 500; i++) sb.append("\"k").append(i).append("\":").append(i).append(',');
        sb.append("\"a\": 1 \"b\": 2}"); // missing comma
        byte[] bytes = Utf8.toBytesStd(sb.toString());

        Slime fromBytes = SlimeUtils.jsonToSlime(bytes);
        Slime fromStream = SlimeUtils.jsonToSlime(new ChunkedInputStream(bytes, 3));

        // The error message is parser state, so it must match regardless of chunking.
        assertTrue(fromBytes.get().field("error_message").asString().length() > 0);
        assertEquals(fromBytes.get().field("error_message").asString(),
                     fromStream.get().field("error_message").asString());

        // The full input is not retained when streaming, so the offending input only covers the
        // last buffers read, prefixed with a "[... N bytes ...]" marker for the dropped prefix.
        byte[] offendingBytes = fromBytes.get().field("offending_input").asData();
        byte[] offendingStream = fromStream.get().field("offending_input").asData();
        String streamStr = new String(offendingStream, StandardCharsets.UTF_8);
        assertTrue("expected dropped-bytes marker, got: " + streamStr, streamStr.startsWith("[... "));

        int markerEnd = indexOf(offendingStream, (byte) ']') + 1;
        byte[] retainedTail = Arrays.copyOfRange(offendingStream, markerEnd, offendingStream.length);

        // Whatever was retained is a non-empty suffix of what the in-memory path reports.
        assertTrue(retainedTail.length > 0);
        assertTrue("streaming offending input must drop the bulk of the prefix",
                   retainedTail.length < offendingBytes.length);
        byte[] tailOfBytes = Arrays.copyOfRange(offendingBytes,
                                                offendingBytes.length - retainedTail.length,
                                                offendingBytes.length);
        assertArrayEquals(tailOfBytes, retainedTail);
    }

    private static int indexOf(byte[] data, byte b) {
        for (int i = 0; i < data.length; i++) if (data[i] == b) return i;
        return -1;
    }

    @Test
    public void testEncodingUTF8() throws IOException {
        Slime slime = new Slime();
        slime.setString("M\u00E6L");
        ByteArrayOutputStream a = new ByteArrayOutputStream();
        new JsonFormat(true).encode(a, slime);
        String val = a.toString(StandardCharsets.UTF_8);
        assertEquals("\"M\u00E6L\"", val);
    }

    private void verifyEncoding(Slime slime, String expected, boolean compact) {
        try {
            ByteArrayOutputStream a = new ByteArrayOutputStream();
            new JsonFormat(compact).encode(a, slime);
            assertEquals(expected, a.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            fail("Exception thrown when encoding slime: " + e.getMessage());
        }
    }

    private void verifyEncodeDecode(String json, boolean compact) {
        try {
            Slime slime = new Slime();
            new JsonDecoder().decode(slime, Utf8.toBytesStd(json));
            ByteArrayOutputStream a = new ByteArrayOutputStream();
            new JsonFormat(compact).encode(a, slime);
            assertEquals(json, Utf8.toString(a.toByteArray()));
        } catch (Exception e) {
            fail("Exception thrown when encoding slime: " + e.getMessage());
        }
    }

    private String formatDecimal(double value) {
        try {
            Slime slime = new Slime();
            slime.setDouble(value);
            ByteArrayOutputStream a = new ByteArrayOutputStream();
            new JsonFormat(true).encode(a, slime);
            return a.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Test
    public void testDecimalFormat() {
        assertEquals("0.0", formatDecimal(0.0));
        assertEquals("1.0", formatDecimal(1.0));
        assertEquals("2.0", formatDecimal(2.0));
        assertEquals("1.2", formatDecimal(1.2));
        assertEquals("3.333333", formatDecimal(3.333333));
        assertEquals("1.0E20", formatDecimal(1e20));
   }
}
