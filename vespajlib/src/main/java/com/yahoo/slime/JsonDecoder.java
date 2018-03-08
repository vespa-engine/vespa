// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.text.Utf8;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A port of the C++ json decoder intended to be fast.
 *
 * @author Ulf Lilleengen
 */
public class JsonDecoder {

    private BufferedInput in;
    private byte c;

    private final SlimeInserter slimeInserter = new SlimeInserter();
    private final ArrayInserter arrayInserter = new ArrayInserter();
    private final JsonObjectInserter objectInserter = new JsonObjectInserter();
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    private static final byte[] TRUE = {'t', 'r', 'u', 'e'};
    private static final byte[] FALSE = {'f', 'a', 'l', 's', 'e'};
    private static final byte[] NULL = {'n', 'u', 'l', 'l'};
    private static final byte [] SQUARE_BRACKET_OPEN = { '[' };
    private static final byte [] SQUARE_BRACKET_CLOSE = { ']' };
    private static final byte [] CURLY_BRACE_OPEN = { '{' };
    private static final byte [] CURLY_BRACE_CLOSE = { '}' };
    private static final byte [] COLON = { ':' };
    private static final byte COMMA = ',';

    public JsonDecoder() {}

    public Slime decode(Slime slime, byte[] bytes) {
        in = new BufferedInput(bytes);
        next();
        decodeValue(slimeInserter.adjust(slime));
        if (in.failed()) {
            slime.wrap("partial_result");
            slime.get().setData("offending_input", in.getOffending());
            slime.get().setString("error_message", in.getErrorMessage());
        }
        return slime;
    }

    private void decodeValue(Inserter inserter) {
        skipWhiteSpace();
        switch (c) {
            case '"': case '\'': decodeString(inserter); return;
            case '{': decodeObject(inserter); return;
            case '[': decodeArray(inserter); return;
            case 't': expect(TRUE); inserter.insertBOOL(true); return;
            case 'f': expect(FALSE); inserter.insertBOOL(false); return;
            case 'n': expect(NULL); inserter.insertNIX(); return;
            case '-': case '0': case '1': case '2': case '3': case '4': case '5':
            case '6': case '7': case '8': case '9': decodeNumber(inserter); return;
        }
        in.fail("invalid initial character for value");
    }

    @SuppressWarnings("fallthrough")
    private void decodeNumber(Inserter inserter) {
        buf.reset();
        boolean likelyFloatingPoint=false;
        for (;;) {
            switch (c) {
                case '.': case 'e': case 'E':
                    likelyFloatingPoint = true;
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                case '+': case '-':
                    buf.write(c);
                    next();
                    break;
                default:
                    if (likelyFloatingPoint) {
                        double num = Double.parseDouble(Utf8.toString(buf.toByteArray()));
                        inserter.insertDOUBLE(num);
                    } else {
                        long num = Long.parseLong(Utf8.toString(buf.toByteArray()));
                        inserter.insertLONG(num);
                    }
                    return;
            }
        }
    }

    private void expect(byte [] expected) {
        int i;
        for (i = 0; i < expected.length && skip(expected[i]); i++)
            ;
        if (i != expected.length) {
            in.fail("unexpected character");
        }

    }

    private void decodeArray(Inserter inserter) {
        Cursor cursor = inserter.insertARRAY();
        expect(SQUARE_BRACKET_OPEN);
        skipWhiteSpace();
        if (c != ']') {
            do {
                arrayInserter.adjust(cursor);
                decodeValue(arrayInserter);
                skipWhiteSpace();
            } while (skip(COMMA));
        }
        expect(SQUARE_BRACKET_CLOSE);
    }

    private void decodeObject(Inserter inserter) {
        Cursor cursor = inserter.insertOBJECT();
        expect(CURLY_BRACE_OPEN);
        skipWhiteSpace();
        if (c != '}') {
            do {
                skipWhiteSpace();
                String key = readKey();
                skipWhiteSpace();
                expect(COLON);
                objectInserter.adjust(cursor, key);
                decodeValue(objectInserter);
                skipWhiteSpace();
            } while (skip(COMMA));
        }
        expect(CURLY_BRACE_CLOSE);
    }

    private String readKey() {
        buf.reset();
        switch (c) {
        case '"': case '\'': return readString();
        default:
            for (;;) {
                switch (c) {
                case ':': case ' ': case '\t': case '\n': case '\r': case '\0': return Utf8.toString(buf.toByteArray());
                default:
                    buf.write(c);
                    next();
                    break;
                }
            }
        }
    }

    private void decodeString(Inserter inserter) {
        String value = readString();
        inserter.insertSTRING(value);
    }

    private String readString() {
        buf.reset();
        byte quote = c;
        assert(quote == '"' || quote == '\'');
        next();
        for (;;) {
            switch (c) {
            case '\\':
                next();
                switch (c) {
                case '"': case '\\': case '/': case '\'':
                    buf.write(c);
                    break;
                case 'b': buf.write((byte) '\b'); break;
                case 'f': buf.write((byte) '\f'); break;
                case 'n': buf.write((byte) '\n'); break;
                case 'r': buf.write((byte) '\r'); break;
                case 't': buf.write((byte) '\t'); break;
                case 'u': writeUtf8(dequoteUtf16(), buf, 0xffffff80); continue;
                default:
                    in.fail("invalid quoted char(" + c + ")");
                    break;
                }
                next();
                break;
            case '"': case '\'':
                if (c == quote) {
                    next();
                    return Utf8.toString(buf.toByteArray());
                } else {
                    buf.write(c);
                    next();
                }
                break;
            case '\0':
                in.fail("unterminated string");
                return Utf8.toString(buf.toByteArray());
            default:
                buf.write(c);
                next();
                break;
            }
        }
    }

    private static void writeUtf8(long codepoint, ByteArrayOutputStream buf, long mask) {
        if ((codepoint & mask) == 0) {
            buf.write((byte) ((mask << 1) | codepoint));
        } else {
            writeUtf8(codepoint >> 6, buf, mask >> (2 - ((mask >> 6) & 0x1)));
            buf.write((byte) (0x80 | (codepoint & 0x3f)));
        }

    }

    private static byte[] unicodeStart = {'\\', 'u'};
    private long dequoteUtf16() {
        long codepoint = readHexValue(4);
        if (codepoint >= 0xd800) {
            if (codepoint < 0xdc00) { // high
                expect(unicodeStart);
                long low = readHexValue(4);
                if (low >= 0xdc00 && low < 0xe000) {
                    codepoint = 0x10000 + ((codepoint - 0xd800) << 10) + (low - 0xdc00);
                } else {
                    in.fail("missing low surrogate");
                }
            } else if (codepoint < 0xe000) { // low
                in.fail("unexpected low surrogate");
            }
        }
        return codepoint;
    }

    private long readHexValue(int numBytes) {
        long ret = 0;
        for (long i = 0; i < numBytes; ++i) {
            switch (c) {
            case '0': ret = (ret << 4); break;
            case '1': ret = (ret << 4) | 1; break;
            case '2': ret = (ret << 4) | 2; break;
            case '3': ret = (ret << 4) | 3; break;
            case '4': ret = (ret << 4) | 4; break;
            case '5': ret = (ret << 4) | 5; break;
            case '6': ret = (ret << 4) | 6; break;
            case '7': ret = (ret << 4) | 7; break;
            case '8': ret = (ret << 4) | 8; break;
            case '9': ret = (ret << 4) | 9; break;
            case 'a': case 'A': ret = (ret << 4) | 0xa; break;
            case 'b': case 'B': ret = (ret << 4) | 0xb; break;
            case 'c': case 'C': ret = (ret << 4) | 0xc; break;
            case 'd': case 'D': ret = (ret << 4) | 0xd; break;
            case 'e': case 'E': ret = (ret << 4) | 0xe; break;
            case 'f': case 'F': ret = (ret << 4) | 0xf; break;
            default:
                in.fail("invalid hex character");
                return 0;
            }
            next();
        }
        return ret;
    }

    private void next() {
        if (!in.eof()) {
            c = in.getByte();
        } else {
            c = 0;
        }
    }

    private boolean skip(byte x) {
        if (c != x) {
            return false;
        }
        next();
        return true;
    }

    private void skipWhiteSpace() {
        for (;;) {
            switch (c) {
            case ' ': case '\t': case '\n': case '\r':
                next();
                break;
            default: return;
            }
        }
    }

    private static final class JsonObjectInserter implements Inserter {
        private Cursor target;
        private String key;
        public final JsonObjectInserter adjust(Cursor c, String key) {
            target = c;
            this.key = key;
            return this;
        }
        public final Cursor insertNIX()                { return target.setNix(key); }
        public final Cursor insertBOOL(boolean value)  { return target.setBool(key, value); }
        public final Cursor insertLONG(long value)     { return target.setLong(key, value); }
        public final Cursor insertDOUBLE(double value) { return target.setDouble(key, value); }
        public final Cursor insertSTRING(String value) { return target.setString(key, value); }
        public final Cursor insertSTRING(byte[] utf8)  { return target.setString(key, utf8); }
        public final Cursor insertDATA(byte[] value)   { return target.setData(key, value); }
        public final Cursor insertARRAY()              { return target.setArray(key); }
        public final Cursor insertOBJECT()             { return target.setObject(key); }
    }

}
