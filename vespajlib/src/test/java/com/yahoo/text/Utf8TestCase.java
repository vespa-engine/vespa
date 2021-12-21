// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import com.google.common.collect.ImmutableMap;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

import static com.yahoo.text.Lowercase.toLowerCase;
import static com.yahoo.text.Utf8.calculateBytePositions;
import static com.yahoo.text.Utf8.calculateStringPositions;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class Utf8TestCase {

    private static final String TEST_STRING = "This is just sort of random mix. \u5370\u57df\u60c5\u5831\u53EF\u4EE5\u6709x\u00e9\u00e8";
    private static final int[] TEST_CODEPOINTS = {0x0, 0x7f, 0x80, 0x7ff, 0x800, 0xd7ff, 0xe000, 0xffff, 0x10000, 0x10ffff,
            0x34, 0x355, 0x2567, 0xfff, 0xe987, 0x100abc
    };

    public void dumpSome() throws java.io.IOException {
        int i = 32;
        int j = 3;
        int cnt = 0;
        while (i < 0x110000) {
            if (i < 0xD800 || i >= 0xE000) ++cnt;
            i += j;
            ++j;
        }
        System.out.println("allocate "+cnt+" array entries");
        int codes[] = new int[cnt];
        i = 32;
        j = 3;
        cnt = 0;
        while (i < 0x110000) {
            if (i < 0xD800 || i >= 0xE000) codes[cnt++] = i;
            i += j;
            ++j;
        }
        assertEquals(cnt, codes.length);
        System.out.println("fill "+cnt+" array entries");
        String str = new String(codes, 0, cnt);
        byte[] arr = Utf8.toBytes(str);
        java.io.FileOutputStream fos = new java.io.FileOutputStream("random-long-utf8.dat");
        fos.write(arr);
        fos.close();
    }

    public void dumpMore() throws java.io.IOException {
        java.text.Normalizer.Form form = java.text.Normalizer.Form.NFKC;

        java.io.FileOutputStream fos = new java.io.FileOutputStream("lowercase-table.dat");
        for (int i = 0; i < 0x110000; i++) {
            StringBuilder b = new StringBuilder();
            b.appendCodePoint(i);
            String n1 = b.toString();
            String n2 = java.text.Normalizer.normalize(b, form);
            if (n1.equals(n2)) {
                String l = toLowerCase(n1);
                int chars = l.length();
                int codes = l.codePointCount(0, chars);
                if (codes != 1) {
                    System.out.println("codepoint "+i+" transformed into "+codes+" codepoints: "+n1+" -> "+l);
                } else {
                    int lc = l.codePointAt(0);
                    if (lc != i) {
                        String o = "lowercase( "+i+" )= "+lc+"\n";
                        byte[] arr = Utf8.toBytes(o);
                        fos.write(arr);
                    }
                }
            }
        }
        fos.close();
    }

    @Test
    public void testSimple() {
        String s1 = "test";
        String s2 = "f\u00F8rst";
        String s3 = "\u00C5pen";
        byte[] b4 = { (byte) 0xE5, (byte) 0xA4, (byte) 0x89, (byte) 0xE6,
                (byte) 0x85, (byte) 0x8B };

        byte[] b1 = Utf8.toBytes(s1);
        byte[] b2 = Utf8.toBytes(s2);
        byte[] b3 = Utf8.toBytes(s3);
        String s4 = Utf8.toString(b4);

        assertEquals('t', b1[0]);
        assertEquals('e', b1[1]);
        assertEquals('s', b1[2]);
        assertEquals('t', b1[3]);

        assertEquals('f', b2[0]);
        assertEquals((byte) 0xC3, b2[1]);
        assertEquals((byte) 0xB8, b2[2]);
        assertEquals('r', b2[3]);
        assertEquals('s', b2[4]);
        assertEquals('t', b2[5]);

        assertEquals((byte) 0xC3, b3[0]);
        assertEquals((byte) 0x85, b3[1]);
        assertEquals('p', b3[2]);
        assertEquals('e', b3[3]);
        assertEquals('n', b3[4]);

        assertEquals('\u5909', s4.charAt(0));
        assertEquals('\u614B', s4.charAt(1));

        String ss1 = Utf8.toString(b1);
        String ss2 = Utf8.toString(b2);
        String ss3 = Utf8.toString(b3);
        byte[] bb4 = Utf8.toBytes(s4);

        assertEquals(s1, ss1);
        assertEquals(s3, ss3);
        assertEquals(s2, ss2);
        assertEquals(Utf8.toString(b4), Utf8.toString(bb4));
    }

    private int javaCountBytes(String str) {
        byte[] octets = Utf8.toBytes(str);
        return octets.length;
    }

    private String makeString(int codePoint) {
        char[] chars = Character.toChars(codePoint);
        return String.valueOf(chars);
    }

    @Test
    public void testByteCounting() {
        for (int c : TEST_CODEPOINTS) {
            String testCharacter = makeString(c);
            assertEquals(javaCountBytes(testCharacter), Utf8.byteCount(testCharacter));
        }
        assertEquals(javaCountBytes(TEST_STRING), Utf8.byteCount(TEST_STRING));
    }

    @Test
    public void testTotalBytes() {
        //Test with a random mix of
        assertEquals(1,Utf8.totalBytes((byte)0x05));
        assertEquals(4,Utf8.totalBytes((byte)0xF3));
        assertEquals(4,Utf8.totalBytes((byte)0xF0));
        assertEquals(1,Utf8.totalBytes((byte)0x7F));
        assertEquals(2,Utf8.totalBytes((byte)0xC2));
        assertEquals(3,Utf8.totalBytes((byte)0xE0));
    }

    @Test
    public void testUnitCounting() {
        for (int c : TEST_CODEPOINTS) {
            String testCharacter = makeString(c);
            byte[] utf8 = Utf8.toBytes(testCharacter);
            assertEquals(testCharacter.length(), Utf8.unitCount(utf8));
            assertEquals(testCharacter.length(), Utf8.unitCount(utf8[0]));
        }
        byte[] stringAsUtf8 = Utf8.toBytes(TEST_STRING);
        assertEquals(TEST_STRING.length(), Utf8.unitCount(stringAsUtf8));


    }

    @Test
    public void testCumbersomeEncoding() {
        String[] a = {"abc", "def", "ghi\u00e8"};
        int[] aLens = {3, 3, 5};
        CharsetEncoder ce = Utf8.getNewEncoder();
        ByteBuffer forWire = ByteBuffer.allocate(500);

        for (int i = 0; i < a.length; i++) {
            forWire.putInt(aLens[i]);
            Utf8.toBytes(a[i], 0,
                         a[i].length(), forWire, ce);
        }
        forWire.flip();
        int totalLimit = forWire.limit();
        for (String anA : a) {
            int len = forWire.getInt();
            forWire.limit(forWire.position() + len);
            String s = Utf8.toString(forWire);
            assertEquals(anA, s);
            forWire.limit(totalLimit);
        }
        assertEquals(0, forWire.remaining());
    }

    @Test
    public void basic() {
        String foo = "Washington";
        int[] indexes = calculateBytePositions(foo);
        assertThat(indexes.length, is(foo.length() + 1));
        for (int i = 0; i < indexes.length; i++) {
            assertThat(indexes[i], is(i));
        }
    }

    @Test
    public void decodeBasic() {
        byte[] foo = Utf8.toBytes("Washington");
        int[] indexes = calculateStringPositions(foo);
        assertThat(indexes.length, is(foo.length + 1));
        for (int i = 0; i < indexes.length; i++) {
            assertThat(indexes[i], is(i));
        }
    }

    @Test
    public void highBytes() {
        String foo = "\u0128st\u0200e";
        //utf-8
        // 0xC4A8 0x73 0x74 0xC880 0x65
        int[] indexes = calculateBytePositions(foo);
        assertThat(indexes.length, is(foo.length() + 1));
        assertThat(indexes[0], is(0)); //128
        assertThat(indexes[1], is(2)); //s
        assertThat(indexes[2], is(3)); //t
        assertThat(indexes[3], is(4)); //200
        assertThat(indexes[4], is(6)); //e
    }

    @Test
    public void decodeHighBytes() {
        byte[] foo = Utf8.toBytes("\u0128st\u0200e");
        //utf-8
        // 0xC4A8 0x73 0x74 0xC880 0x65
        int[] indexes = calculateStringPositions(foo);
        assertThat(indexes.length, is(foo.length + 1));
        assertThat(indexes[0], is(0)); //128
        assertThat(indexes[1], is(0)); //128
        assertThat(indexes[2], is(1)); //s
        assertThat(indexes[3], is(2)); //t
        assertThat(indexes[4], is(3)); //200
        assertThat(indexes[5], is(3)); //200
        assertThat(indexes[6], is(4)); //e
    }

    @Test
    public void moreHighBytes() {
        String foo = "\u0200\u0201\u0202abc\u0300def\u0301g\u07ff\u0800a\uffffa";
        //utf-8
        //0xC880 0xC881 0xC882 0x61 0x62 0x63 0xCC80 0x64 0x65 0x66 0xCC81 0x67 0xDFBF 0xE0A080 0x61 0xEFBFBF 0x61
        int[] indexes = calculateBytePositions(foo);
        assertThat(indexes.length, is(foo.length() + 1));
        assertThat(indexes[0], is(0)); //200
        assertThat(indexes[1], is(2)); //201
        assertThat(indexes[2], is(4)); //202
        assertThat(indexes[3], is(6)); //a
        assertThat(indexes[4], is(7)); //b
        assertThat(indexes[5], is(8)); //c
        assertThat(indexes[6], is(9)); //300
        assertThat(indexes[7], is(11)); //d
        assertThat(indexes[8], is(12)); //e
        assertThat(indexes[9], is(13)); //f
        assertThat(indexes[10], is(14)); //301
        assertThat(indexes[11], is(16)); //g
        assertThat(indexes[12], is(17)); //7ff
        assertThat(indexes[13], is(19)); //800
        assertThat(indexes[14], is(22)); //a
        assertThat(indexes[15], is(23)); //ffff
        assertThat(indexes[16], is(26)); //a
    }

    @Test
    public void decodeMoreHighBytes() {
        String foo = "\u0200\u0201\u0202abc\u0300def\u0301g\u07ff\u0800a\uffffa";
        //utf-8
        //0xC880 0xC881 0xC882 0x61 0x62 0x63 0xCC80 0x64 0x65 0x66 0xCC81 0x67 0xDFBF 0xE0A080 0x61 0xEFBFBF 0x61
        int[] indexes = calculateStringPositions(Utf8.toBytes(foo));
        assertThat(indexes.length, is(28));
        assertThat(indexes[0], is(0)); //200
        assertThat(indexes[1], is(0)); //200
        assertThat(indexes[2], is(1)); //201
        assertThat(indexes[3], is(1)); //201
        assertThat(indexes[4], is(2)); //202
        assertThat(indexes[5], is(2)); //202
        assertThat(indexes[6], is(3)); //a
        assertThat(indexes[7], is(4)); //b
        assertThat(indexes[8], is(5)); //c
        assertThat(indexes[9], is(6)); //300
        assertThat(indexes[10], is(6)); //300
        assertThat(indexes[11], is(7)); //d
        assertThat(indexes[12], is(8)); //e
        assertThat(indexes[13], is(9)); //f
        assertThat(indexes[14], is(10)); //301
        assertThat(indexes[15], is(10)); //301
        assertThat(indexes[16], is(11)); //g
        assertThat(indexes[17], is(12)); //7ff
        assertThat(indexes[18], is(12)); //7ff
        assertThat(indexes[19], is(13)); //800
        assertThat(indexes[20], is(13)); //800
        assertThat(indexes[21], is(13)); //800
        assertThat(indexes[22], is(14)); //a
        assertThat(indexes[23], is(15)); //ffff
        assertThat(indexes[24], is(15)); //ffff
        assertThat(indexes[25], is(15)); //ffff
        assertThat(indexes[26], is(16)); //a
    }

    @Test
    public void testOptimisticEncoder() {
        for (char i=0; i < 256; i++) {
            StringBuilder sb = new StringBuilder();
            for (char c=0; c < i; c++) {
                sb.append(c);
            }
            assertTrue(Arrays.equals(Utf8.toBytesStd(sb.toString()), Utf8.toBytes(sb.toString())));
        }
    }

    @Test
    public void testLong()
    {
        for (long l=-0x10000; l < 0x10000; l++) {
            assertLongEquals(l);
        }
        assertLongEquals(Long.MAX_VALUE);
        assertLongEquals(Long.MIN_VALUE);
    }

    private void assertLongEquals(long l) {
        byte [] a = Utf8.toBytes(String.valueOf(l));
        byte [] b = Utf8.toAsciiBytes(l);
        if (!Arrays.equals(a, b)) {
            assertTrue(Arrays.equals(a, b));
        }
    }

    @Test
    public void testBoolean() {
        assertEquals("true", String.valueOf(true));
        assertEquals("false", String.valueOf(false));
        assertTrue(Arrays.equals(Utf8.toAsciiBytes(true), new Utf8String(String.valueOf(true)).getBytes()));
        assertTrue(Arrays.equals(Utf8.toAsciiBytes(false), new Utf8String(String.valueOf(false)).getBytes()));
    }
    @Test
    public void testInt()
    {
        for (int l=-0x10000; l < 0x10000; l++) {
            byte [] a = Utf8.toBytes(String.valueOf(l));
            byte [] b = Utf8.toAsciiBytes(l);
            if (!Arrays.equals(a, b)) {
                assertTrue(Arrays.equals(a, b));
            }
        }
    }
    @Test
    public void testShort()
    {
        for (short l=-0x1000; l < 0x1000; l++) {
            byte [] a = Utf8.toBytes(String.valueOf(l));
            byte [] b = Utf8.toAsciiBytes(l);
            if (!Arrays.equals(a, b)) {
                assertTrue(Arrays.equals(a, b));
            }
        }
    }

    @Test
    public void surrogatePairs() {
        String foo = "a\uD800\uDC00b";
        //unicode
        //0x61 0x10000 0x62
        //utf-16
        //0x61 0xD800DC00 0x62
        //utf-8
        //0x61 0xF0908080 0x62
        int[] indexes = calculateBytePositions(foo);
        assertThat(indexes.length, is(foo.length() + 1));
        assertThat(indexes[0], is(0)); //a
        assertThat(indexes[1], is(1)); //10000
        assertThat(indexes[2], is(1)); //10000, second of surrogate pair
        assertThat(indexes[3], is(5)); //b
    }

    @Test
    public void decodeSurrogatePairs() {
        String foo = "a\uD800\uDC00b";
        //unicode
        //0x61 0x10000 0x62
        //utf-16
        //0x61 0xD800DC00 0x62
        //utf-8
        //0x61 0xF0908080 0x62
        int[] indexes = calculateStringPositions(Utf8.toBytes(foo));
        assertThat(indexes.length, is(7));
        assertThat(indexes[0], is(0)); //a
        assertThat(indexes[1], is(1)); //10000
        assertThat(indexes[2], is(1)); //10000
        assertThat(indexes[3], is(1)); //10000
        assertThat(indexes[4], is(1)); //10000
        assertThat(indexes[5], is(2)); //b
    }

    @Test
    public void encodeStartEndPositions() {
        String foo = "abcde";
        int start = 0;
        int length = foo.length(); //5
        int end = start + length;

        int[] indexes = calculateBytePositions(foo);
        int byteStart = indexes[start];
        int byteEnd = indexes[end];
        int byteLength = byteEnd - byteStart;

        assertThat(byteStart, equalTo(start));
        assertThat(byteEnd, equalTo(end));
        assertThat(byteLength, equalTo(length));
    }

    @Test
    public void encodeStartEndPositionsMultibyteCharsAtEnd() {
        String foo = "\u0200abcde\uD800\uDC00";
        int start = 0;
        int length = foo.length(); //8
        int end = start + length;

        int[] indexes = calculateBytePositions(foo);
        int byteStart = indexes[start];
        int byteEnd = indexes[end];
        int byteLength = byteEnd - byteStart;

        //utf-8
        //0xC880 a b c d e 0xD800DC00

        assertThat(byteStart, equalTo(start));
        assertThat(byteEnd, equalTo(11));
        assertThat(byteLength, equalTo(11));
    }

    @Test
    public void decodeStartEndPositions() {
        byte[] foo = Utf8.toBytes("abcde");
        int start = 0;
        int length = foo.length;  //5
        int end = start + length;

        int[] indexes = calculateStringPositions(foo);
        int stringStart = indexes[start];
        int stringEnd = indexes[end];
        int stringLength = stringEnd - stringStart;

        assertThat(stringStart, equalTo(start));
        assertThat(stringEnd, equalTo(end));
        assertThat(stringLength, equalTo(length));
    }

    @Test
    public void decodeStartEndPositionsMultibyteCharsAtEnd() {
        byte[] foo = Utf8.toBytes("\u0200abcde\uD800\uDC00");
        int start = 0;
        int length = foo.length; //11
        int end = start + length;

        int[] indexes = calculateStringPositions(foo);
        int stringStart = indexes[start];
        int stringEnd = indexes[end];
        int stringLength = stringEnd - stringStart;

        //utf-8
        //0xC880 a b c d e 0xD800DC00

        assertThat(stringStart, equalTo(start));
        assertThat(stringEnd, equalTo(8));
        assertThat(stringLength, equalTo(8));
    }

    @Test
    public void emptyInputStringResultsInArrayWithSingleZero() {
        byte[] empty = new byte[] {};
        int[] indexes = calculateStringPositions(empty);
        assertThat(indexes.length, is(1));
        assertThat(indexes[0], is(0));
    }

    @Test
    public void testEncoding() {
        for (int c : TEST_CODEPOINTS) {
            byte[] encoded = Utf8.encode(c);
            String testCharacter = makeString(c);
            byte[] utf8 = Utf8.toBytes(testCharacter);
            assertArrayEquals(utf8, encoded);
        }
        byte[] stringAsUtf8 = Utf8.toBytes(TEST_STRING);
        byte[] handEncoded = new byte[Utf8.byteCount(TEST_STRING)];
        for (int i = 0, j = 0; i < TEST_STRING.length(); i = TEST_STRING.offsetByCodePoints(i, 1)) {
            j = Utf8.encode(TEST_STRING.codePointAt(i), handEncoded, j);
        }
        assertArrayEquals(stringAsUtf8, handEncoded);
    }

    @Test
    public void testStreamEncoding() throws IOException {
        for (int c : TEST_CODEPOINTS) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Utf8.encode(c, buffer);
            byte[] encoded = buffer.toByteArray();
            String testCharacter = makeString(c);
            byte[] utf8 = Utf8.toBytes(testCharacter);
            assertArrayEquals(utf8, encoded);
        }
        byte[] stringAsUtf8 = Utf8.toBytes(TEST_STRING);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < TEST_STRING.length(); i = TEST_STRING.offsetByCodePoints(i, 1)) {
            Utf8.encode(TEST_STRING.codePointAt(i), buffer);
        }
        byte[] handEncoded = buffer.toByteArray();
        assertArrayEquals(stringAsUtf8, handEncoded);
    }

    @Test
    public void testByteBufferEncoding() {
        for (int c : TEST_CODEPOINTS) {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            Utf8.encode(c, buffer);
            byte[] encoded = new byte[buffer.position()];
            buffer.flip();
            for (int i = 0; i < encoded.length; ++i) {
                encoded[i] = buffer.get();
            }
            String testCharacter = makeString(c);
            byte[] utf8 = Utf8.toBytes(testCharacter);
            assertArrayEquals(utf8, encoded);
        }
        byte[] stringAsUtf8 = Utf8.toBytes(TEST_STRING);
        ByteBuffer buffer = ByteBuffer.allocate(TEST_STRING.length() * 4);
        for (int i = 0; i < TEST_STRING.length(); i = TEST_STRING.offsetByCodePoints(i, 1)) {
            Utf8.encode(TEST_STRING.codePointAt(i), buffer);
        }
        byte[] handEncoded = new byte[buffer.position()];
        buffer.flip();
        for (int i = 0; i < handEncoded.length; ++i) {
            handEncoded[i] = buffer.get();
        }
        assertArrayEquals(stringAsUtf8, handEncoded);
    }

    @Test
    @Ignore
    public void benchmarkDecoding() {
        byte[] ascii = "This is just sort of random mix.".getBytes();
        byte[] unicode = "This is just sort of random mix. \u5370\u57df\u60c5\u5831\u53EF\u4EE5\u6709x\u00e9\u00e8".getBytes(StandardCharsets.UTF_8);
        int iterations = 100_000; // Use 100_000+ for benchmarking

        ImmutableMap.of("ascii", ascii, "unicode", unicode).forEach((type, b) -> {
            long time1 = benchmark(() -> decode(Utf8::toString, b, iterations));
            System.out.printf("Utf8::toString of %s string took %d ms\n", type, time1);
            long time2 = benchmark(() -> decode((b1) -> new String(b1, StandardCharsets.UTF_8), b, iterations));
            System.out.printf("String::new of %s string took %d ms\n", type, time2);
            double change = ((double) time2 / (double) time1) - 1;
            System.out.printf("Change = %.02f%%\n", change * 100);
        });
    }

    @Test
    @Ignore
    public void benchmarkEncoding() {
        String ascii = "This is just sort of random mix.";
        String unicode = "This is just sort of random mix. \u5370\u57df\u60c5\u5831\u53EF\u4EE5\u6709x\u00e9\u00e8";
        int iterations = 1_000_000; // Use 1_000_000+ for benchmarking

        ImmutableMap.of("ascii", ascii, "unicode", unicode).forEach((type, s) -> {
            long time1 = benchmark(() -> encode(Utf8::toBytes, s, iterations));
            System.out.printf("Utf8::toBytes of %s string took %d ms\n", type, time1);
            long time2 = benchmark(() -> encode((s1) -> s1.getBytes(StandardCharsets.UTF_8), s, iterations));
            System.out.printf("String::getBytes of %s string took %d ms\n", type, time2);
            double change = ((double) time2 / (double) time1) - 1;
            System.out.printf("Change = %.02f%%\n", change * 100);
        });
    }


    private byte[] encode(Function<String, byte[]> encoder, String s, int iterations) {
        byte[] res = null;
        for (int i = 0; i < iterations; i++) {
            res = encoder.apply(s + i); // Append counter to avoid String cache
        }
        return res;
    }

    private String decode(Function<byte[], String> decoder, byte[] b, int iterations) {
        String res = null;
        for (int i = 0; i < iterations; i++) {
            // Append counter to avoid String cache
            byte[] counter = String.valueOf(i).getBytes();
            byte[] result = new byte[b.length + counter.length];
            System.arraycopy(b, 0, result, 0, b.length);
            System.arraycopy(counter, 0, result, b.length, counter.length);
            res = decoder.apply(result);
        }
        return res;
    }

    private long benchmark(Runnable r) {
        r.run(); // Warmup
        long start = System.currentTimeMillis();
        r.run();
        long end = System.currentTimeMillis();
        return end - start;
    }

}
