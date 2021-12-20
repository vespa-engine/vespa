// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import org.junit.Test;

import static com.yahoo.slime.BinaryFormat.decode_double;
import static com.yahoo.slime.BinaryFormat.decode_meta;
import static com.yahoo.slime.BinaryFormat.decode_type;
import static com.yahoo.slime.BinaryFormat.decode_zigzag;
import static com.yahoo.slime.BinaryFormat.encode_double;
import static com.yahoo.slime.BinaryFormat.encode_type_and_meta;
import static com.yahoo.slime.BinaryFormat.encode_zigzag;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class BinaryFormatTestCase {

    static final int TYPE_LIMIT = 8;
    static final int META_LIMIT = 32;
    static final int MAX_NUM_SIZE = 8;

    static byte enc_t_and_sz(Type t, int size) {
        assert size <= 30;
        return encode_type_and_meta(t.ID, size + 1);
    }
    static byte enc_t_and_m(Type t, int meta) {
        assert meta <= 31;
        return encode_type_and_meta(t.ID, meta);
    }

    void verify_cmpr_long(long value, byte[] expect) {
        BufferedOutput output = new BufferedOutput();
        BinaryEncoder bof = new BinaryEncoder(output);
        bof.encode_cmpr_long(value);
        byte[] actual = output.toArray();
        assertThat(actual, is(expect));

        BinaryDecoder bif = new BinaryDecoder();
        bif.in = new BufferedInput(expect);
        long got = bif.read_cmpr_long();
        assertThat(got, is(value));
    }

    // was verifyBasic
    void verifyEncoding(Slime slime, byte[] expect) {
        assertThat(BinaryFormat.encode(slime), is(expect));
        Compressor compressor = new Compressor(CompressionType.LZ4, 3, 2, 0);
        Compressor.Compression result = BinaryFormat.encode_and_compress(slime, compressor);
        byte [] decompressed = compressor.decompress(result);
        assertThat(decompressed, is(expect));
        verifyMultiEncode(expect);
    }

    void verifyMultiEncode(byte[] expect) {
        byte[][] buffers = new byte[6][];
        buffers[0] = expect;

        for (int i = 0; i < 5; ++i) {
            Slime slime = BinaryFormat.decode(buffers[i]);
            buffers[i+1] = BinaryFormat.encode(slime);
            assertThat(buffers[i+1], is(expect));
        }
    }

    @Test
    public void testZigZagConversion() {
        assertThat(encode_zigzag(0), is((long)0));
        assertThat(decode_zigzag(encode_zigzag(0)), is(0L));

        assertThat(encode_zigzag(-1), is(1L));
        assertThat(decode_zigzag(encode_zigzag(-1)), is(-1L));

        assertThat(encode_zigzag(1), is(2L));
        assertThat(decode_zigzag(encode_zigzag(1)), is(1L));

        assertThat(encode_zigzag(-2), is(3L));
        assertThat(decode_zigzag(encode_zigzag(-2)), is(-2L));

        assertThat(encode_zigzag(2), is(4L));
        assertThat(decode_zigzag(encode_zigzag(2)), is(2L));

        assertThat(encode_zigzag(-1000), is(1999L));
        assertThat(decode_zigzag(encode_zigzag(-1000)), is(-1000L));

        assertThat(encode_zigzag(1000), is(2000L));
        assertThat(decode_zigzag(encode_zigzag(1000)), is(1000L));

        assertThat(encode_zigzag(-0x8000000000000000L), is(-1L));
        assertThat(decode_zigzag(encode_zigzag(-0x8000000000000000L)), is(-0x8000000000000000L));

        assertThat(encode_zigzag(0x7fffffffffffffffL), is(-2L));
        assertThat(decode_zigzag(encode_zigzag(0x7fffffffffffffffL)), is(0x7fffffffffffffffL));
    }

    @Test
    public void testDoubleConversion() {
        assertThat(encode_double(0.0), is(0L));
        assertThat(decode_double(encode_double(0.0)), is(0.0));

        assertThat(encode_double(1.0), is(0x3ff0000000000000L));
        assertThat(decode_double(encode_double(1.0)), is(1.0));

        assertThat(encode_double(-1.0), is(0xbff0000000000000L));
        assertThat(decode_double(encode_double(-1.0)), is(-1.0));

        assertThat(encode_double(2.0), is(0x4000000000000000L));
        assertThat(decode_double(encode_double(2.0)), is(2.0));

        assertThat(encode_double(-2.0), is(0xc000000000000000L));
        assertThat(decode_double(encode_double(-2.0)), is(-2.0));

        assertThat(encode_double(-0.0), is(0x8000000000000000L));
        assertThat(decode_double(encode_double(-0.0)), is(-0.0));

        assertThat(encode_double(3.5), is(0x400c000000000000L));
        assertThat(decode_double(encode_double(3.5)), is(3.5));

        assertThat(encode_double(65535.875), is(0x40EFFFFC00000000L));
        assertThat(decode_double(encode_double(65535.875)), is(65535.875));
    }

    @Test
    public void testTypeAndMetaMangling() {
        for (byte type = 0; type < TYPE_LIMIT; ++type) {
            for (int meta = 0; meta < META_LIMIT; ++meta) {
                byte mangled = encode_type_and_meta(type, meta);
                assertThat(decode_type(mangled).ID, is(type));
                assertThat(decode_meta(mangled), is(meta));
            }
        }
    }

    @Test
    public void testCompressedLong() {
        {
            long value = 0;
            byte[] wanted = { 0 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 127;
            byte[] wanted = { 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 128;
            byte[] wanted = { -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 16383;
            byte[] wanted = { -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 16384;
            byte[] wanted = { -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 2097151;
            byte[] wanted = { -1, -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 2097152;
            byte[] wanted = { -128, -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 268435455;
            byte[] wanted = { -1, -1, -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 268435456;
            byte[] wanted = { -128, -128, -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 34359738367L;
            byte[] wanted = { -1, -1, -1, -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 34359738368L;
            byte[] wanted = { -128, -128, -128, -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 4398046511103L;
            byte[] wanted = { -1, -1, -1, -1, -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 4398046511104L;
            byte[] wanted = { -128, -128, -128, -128, -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 562949953421311L;
            byte[] wanted = { -1, -1, -1, -1, -1, -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 562949953421312L;
            byte[] wanted = { -128, -128, -128, -128, -128, -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 72057594037927935L;
            byte[] wanted = { -1, -1, -1, -1, -1, -1, -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 72057594037927936L;
            byte[] wanted = { -128, -128, -128, -128, -128, -128, -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = 9223372036854775807L;
            byte[] wanted = { -1, -1, -1, -1, -1, -1, -1, -1, 127 };
            verify_cmpr_long(value, wanted);
        }{
            long value = -9223372036854775808L;
            byte[] wanted = { -128, -128, -128, -128, -128, -128, -128, -128, -128, 1 };
            verify_cmpr_long(value, wanted);
        }{
            long value = -1;
            byte[] wanted = { -1, -1, -1, -1, -1, -1, -1, -1, -1, 1 };
            verify_cmpr_long(value, wanted);
        }
    }

    // testWriteByte -> buffered IO test
    // testWriteBytes -> buffered IO test
    // testReadByte -> buffered IO test
    // testReadBytes -> buffered IO test
    @Test
    public void testTypeAndSizeConversion() {
        for (byte type = 0; type < TYPE_LIMIT; ++type) {
            for (long size = 0; size < 500; ++size) {
                BufferedOutput expect = new BufferedOutput();
                BufferedOutput actual = new BufferedOutput();

                if ((size + 1) < META_LIMIT) {
                    expect.put(encode_type_and_meta((int)type, (int)(size +1)));
                } else {
                    expect.put(type);
                    BinaryEncoder encoder = new BinaryEncoder(expect);
                    encoder.encode_cmpr_long(size);
                }
                {
                    BinaryEncoder encoder = new BinaryEncoder(actual);
                    encoder.write_type_and_size(type, size);
                }
                assertThat(actual.toArray(), is(expect.toArray()));

                byte[] got = expect.toArray();
                BinaryDecoder bif = new BinaryDecoder();
                bif.in = new BufferedInput(got);
                byte b = bif.in.getByte();
                Type decodedType = decode_type(b);
                long decodedSize = bif.read_size(decode_meta(b));
                assertThat(decodedType.ID, is(type));
                assertThat(decodedSize, is(size));
                assertThat(bif.in.getConsumedSize(), is(got.length));
                assertThat(bif.in.failed(), is(false));
            }
        }

    }

    static long build_bits(int type, int n, int pre, boolean hi, BufferedOutput expect) {
        long value = 0;
        expect.put(encode_type_and_meta(type, n));
        for (int i = 0; i < n; ++i) {
            byte b = (i < pre) ? 0x00 : (byte)(0x11 * (i - pre + 1));
            expect.put(b);
            int shift = hi ? ((7 - i) * 8) : (i * 8);
            long bits = b & 0xff;
            value |= bits << shift;
        }
        return value;
    }

    @Test
    public void testEncodingAndDecodingOfTypeAndBytes() {
        for (byte type = 0; type < TYPE_LIMIT; ++type) {
            for (int n = 0; n < MAX_NUM_SIZE; ++n) {
                for (int pre = 0; (pre == 0) || (pre < n); ++pre) {
                    for (int hi = 0; hi < 2; ++hi) {
                        BufferedOutput expbuf = new BufferedOutput();
                        long bits = build_bits(type, n, pre, (hi != 0), expbuf);
                        byte[] expect = expbuf.toArray();

                        // test output:
                        BufferedOutput output = new BufferedOutput();
                        BinaryEncoder bof = new BinaryEncoder(output);
                        if (hi != 0) {
                            bof.write_type_and_bytes_be(type, bits);
                        } else {
                            bof.write_type_and_bytes_le(type, bits);
                        }
                        byte[] actual = output.toArray();
                        assertThat(actual, is(expect));

                        // test input:
                        BinaryDecoder bif = new BinaryDecoder();
                        bif.in = new BufferedInput(expect);
                        int size = decode_meta(bif.in.getByte());
                        long decodedBits = (hi != 0) ? bif.read_bytes_be(size) : bif.read_bytes_le(size);
                        assertThat(decodedBits, is(bits));
                        assertThat(bif.in.getConsumedSize(), is(expect.length));
                        assertThat(bif.in.failed(), is(false));
                    }
                }
            }
        }
    }

    @Test
    public void testEncodingEmptySlime() {
        Slime slime = new Slime();
        BufferedOutput expect = new BufferedOutput();
        expect.put((byte)0); // num symbols
        expect.put((byte)0); // nix
        byte[] actual = BinaryFormat.encode(slime);

        assertThat(actual, is(expect.toArray()));
        verifyMultiEncode(expect.toArray());
    }

    @Test
    public void testEncodingSlimeHoldingASingleBasicValue() {
        {
            Slime slime = new Slime();
            slime.setBool(false);
            byte[] expect = { 0, Type.BOOL.ID };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setBool(true);
            byte[] expect = { 0, enc_t_and_m(Type.BOOL, 1) };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setLong(0);
            byte[] expect = { 0, Type.LONG.ID };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setLong(13);
            byte[] expect = { 0, enc_t_and_m(Type.LONG, 1), 13*2 };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setLong(-123456789);
            final long ev = (2 * 123456789) - 1;
            byte b1 = (byte)(ev);
            byte b2 = (byte)(ev >>  8);
            byte b3 = (byte)(ev >> 16);
            byte b4 = (byte)(ev >> 24);

            byte[] expect = { 0, enc_t_and_m(Type.LONG, 4), b1, b2, b3, b4 };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setDouble(0.0);
            byte[] expect = { 0, Type.DOUBLE.ID };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setDouble(1.0);
            byte[] expect = { 0, enc_t_and_m(Type.DOUBLE, 2), (byte)0x3f, (byte)0xf0 };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setString("");
            byte[] expect = { 0, enc_t_and_sz(Type.STRING, 0) };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setString("fo");
            byte[] expect = { 0, enc_t_and_sz(Type.STRING, 2), 'f', 'o' };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
            byte[] expect = { 0, Type.STRING.ID, 26*2,
                              'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
                              'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
                              'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
                              'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F',
                              'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
                              'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
                              'W', 'X', 'Y', 'Z'
            };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            slime.setData(new byte[0]);
            byte[] expect = { 0, enc_t_and_sz(Type.DATA, 0) };
            verifyEncoding(slime, expect);
        }
        {
            Slime slime = new Slime();
            byte[] data = { 42, -123 };
            slime.setData(data);
            byte[] expect = { 0, enc_t_and_sz(Type.DATA, 2), 42, -123 };
            verifyEncoding(slime, expect);
        }
    }

    @Test
    public void testBufferedInputWithOffset() {
        Slime slime = new Slime();
        byte[] data = { 42, -123 };
        slime.setData(data);
        byte[] expect = { 0, enc_t_and_sz(Type.DATA, 2), 42, -123 };
        verifyEncoding(slime, expect);
        byte [] overlappingBuffer = new byte [expect.length + 2];
        System.arraycopy(expect, 0, overlappingBuffer, 1, expect.length);
        overlappingBuffer[overlappingBuffer.length - 1] = 0;
        Slime copy = BinaryFormat.decode(overlappingBuffer, 1, expect.length);
        assertThat(BinaryFormat.encode(slime), is(BinaryFormat.encode(copy)));
    }

    @Test
    public void testEncodingSlimeArray() {
        Slime slime = new Slime();
        Cursor c = slime.setArray();
        byte[] data = { 'd', 'a', 't', 'a' };
        c.addNix();
        c.addBool(true);
        c.addLong(42);
        c.addDouble(3.5);
        c.addString("string");
        c.addData(data);
        byte[] expect = {
            0, // num symbols
            enc_t_and_sz(Type.ARRAY, 6), // value type and size
            0, // nix
            enc_t_and_m(Type.BOOL, 1),
            enc_t_and_m(Type.LONG, 1), 42*2,
            enc_t_and_m(Type.DOUBLE, 2), 0x40, 0x0c, // 3.5
            enc_t_and_sz(Type.STRING, 6), 's', 't', 'r', 'i', 'n', 'g',
            enc_t_and_sz(Type.DATA, 4), 'd', 'a', 't', 'a'
        };
        verifyEncoding(slime, expect);
    }

    @Test
    public void testEncodingSlimeObject() {
        Slime slime = new Slime();
        Cursor c = slime.setObject();
        byte[] data = { 'd', 'a', 't', 'a' };
        c.setNix("a");
        c.setBool("b", true);
        c.setLong("c", 42);
        c.setDouble("d", 3.5);
        c.setString("e", "string");
        c.setData("f", data);
        byte[] expect = {
            6, // num symbols
            1, 'a', 1, 'b', 1, 'c', 1, 'd', 1, 'e', 1, 'f', // symbol table
            enc_t_and_sz(Type.OBJECT, 6), // value type and size
            0, 0, // nix
            1, enc_t_and_m(Type.BOOL, 1),
            2, enc_t_and_m(Type.LONG, 1), 42*2,
            3, enc_t_and_m(Type.DOUBLE, 2), 0x40, 0x0c, // 3.5
            4, enc_t_and_sz(Type.STRING, 6), 's', 't', 'r', 'i', 'n', 'g',
            5, enc_t_and_sz(Type.DATA, 4), 'd', 'a', 't', 'a'
        };
        verifyEncoding(slime, expect);
    }

    @Test
    public void testEncodingComplexSlimeStructure() {
        Slime slime = new Slime();
        Cursor c1 = slime.setObject();
        c1.setLong("bar", 10);
        Cursor c2 = c1.setArray("foo");
        c2.addLong(20);
        Cursor c3 = c2.addObject();
        c3.setLong("answer", 42);
        byte[] expect = {
            3, // num symbols
            3, 'b', 'a', 'r',
            3, 'f', 'o', 'o',
            6, 'a', 'n', 's', 'w', 'e', 'r',
            enc_t_and_sz(Type.OBJECT, 2), // value type and size
            0, enc_t_and_m(Type.LONG, 1), 10*2,
            1, enc_t_and_sz(Type.ARRAY, 2), // nested value type and size
            enc_t_and_m(Type.LONG, 1), 20*2,
            enc_t_and_sz(Type.OBJECT, 1), // doubly nested value
            2, enc_t_and_m(Type.LONG, 1), 42*2
        };
        verifyEncoding(slime, expect);
    }

    @Test
    public void testEncodingSlimeReusingSymbols() {
        Slime slime = new Slime();
        Cursor c1 = slime.setArray();
        {
            Cursor c2 = c1.addObject();
            c2.setLong("foo", 10);
            c2.setLong("bar", 20);
        }
        {
            Cursor c2 = c1.addObject();
            c2.setLong("foo", 100);
            c2.setLong("bar", 200);
        }
        byte[] expect = {
            2, // num symbols
            3, 'f', 'o', 'o',
            3, 'b', 'a', 'r',
            enc_t_and_sz(Type.ARRAY, 2), // value type and size
            enc_t_and_sz(Type.OBJECT, 2), // nested value
            0, enc_t_and_m(Type.LONG, 1), 10*2, // foo
            1, enc_t_and_m(Type.LONG, 1), 20*2, // bar
            enc_t_and_sz(Type.OBJECT, 2), // nested value
            0, enc_t_and_m(Type.LONG, 1), (byte)(100*2), // foo
            1, enc_t_and_m(Type.LONG, 2), (byte)144, 1  // bar: 2*200 = 400 = 256 + 144
        };
        verifyEncoding(slime, expect);
    }

    @Test
    public void testDecodingSlimeWithDifferentSymbolOrder() {
        byte[] data = {
            5, // num symbols
            1, 'd', 1, 'e', 1, 'f', 1, 'b', 1, 'c', // symbol table
            enc_t_and_sz(Type.OBJECT, 5), // value type and size
            3, enc_t_and_m(Type.BOOL, 1), // b
            1, enc_t_and_sz(Type.STRING, 6), // e
            's', 't', 'r', 'i', 'n', 'g',
            0, enc_t_and_m(Type.DOUBLE, 2), 0x40, 0x0c, // d
            4, enc_t_and_m(Type.LONG, 1), 5*2, // c
            2, enc_t_and_sz(Type.DATA, 4), // f
            'd', 'a', 't', 'a'
        };
        BinaryDecoder decoder = new BinaryDecoder();
        Slime slime = decoder.decode(data);
        int consumed = decoder.in.getConsumedSize();
        assertThat(consumed, is(data.length));
        Cursor c = slime.get();
        assertThat(c.valid(), is(true));
        assertThat(c.type(), is(Type.OBJECT));
        assertThat(c.children(), is(5));
        assertThat(c.field("b").asBool(), is(true));
        assertThat(c.field("c").asLong(), is(5L));
        assertThat(c.field("d").asDouble(), is(3.5));
        assertThat(c.field("e").asString(), is("string"));
        byte[] expd = { 'd', 'a', 't', 'a' };
        assertThat(c.field("f").asData(), is(expd));
        assertThat(c.entry(5).valid(), is(false)); // not ARRAY
    }

}
