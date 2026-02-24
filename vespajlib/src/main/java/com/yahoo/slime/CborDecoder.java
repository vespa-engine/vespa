// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Decoder for reading CBOR (RFC 8949) encoded data into a Slime object.
 *
 * Mapping from CBOR to Slime types:
 *   CBOR unsigned/negative integer  ->  Slime LONG
 *   CBOR byte string                ->  Slime DATA
 *   CBOR text string                ->  Slime STRING
 *   CBOR array                      ->  Slime ARRAY
 *   CBOR map (text string keys)     ->  Slime OBJECT
 *   CBOR false/true                 ->  Slime BOOL
 *   CBOR null/undefined             ->  Slime NIX
 *   CBOR half/single/double float   ->  Slime DOUBLE
 *   CBOR tag                        ->  (tag ignored, inner value decoded)
 */
final class CborDecoder {

    BufferedInput in;

    private final SlimeInserter slimeInserter = new SlimeInserter(null);
    private final ArrayInserter arrayInserter = new ArrayInserter(null);
    private final ObjectInserter objectInserter = new ObjectInserter(null, "");

    public CborDecoder() {}

    public Slime decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }

    public Slime decode(byte[] bytes, int offset, int length) {
        Slime slime = new Slime();
        in = new BufferedInput(bytes, offset, length);
        decodeValue(slimeInserter.adjust(slime));
        if (in.failed()) {
            slime.wrap("partial_result");
            slime.get().setData("offending_input", in.getOffending());
            slime.get().setString("error_message", in.getErrorMessage());
        }
        return slime;
    }

    long readUint(int bytes) {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            value = (value << 8) | (in.getByte() & 0xffL);
        }
        return value;
    }

    long readArgument(int additional) {
        if (additional <= 23) return additional;
        return switch (additional) {
            case 24 -> in.getByte() & 0xffL;
            case 25 -> readUint(2);
            case 26 -> readUint(4);
            case 27 -> readUint(8);
            default -> {
                in.fail("unsupported CBOR additional info: " + additional);
                yield 0;
            }
        };
    }

    byte[] readBytes(int expectedMajor, int additional) {
        if (additional != 31) {
            return in.getBytes((int) readArgument(additional));
        }
        BufferedOutput buf = new BufferedOutput();
        for (;;) {
            byte next = in.getByte();
            if (in.failed() || next == BREAK) break;
            int chunkMajor = (next & 0xff) >> 5;
            int chunkAdditional = next & 0x1f;
            if (chunkMajor != expectedMajor) {
                in.fail("expected major type " + expectedMajor + " chunk, got " + chunkMajor);
                break;
            }
            int len = (int) readArgument(chunkAdditional);
            buf.put(in.getBytes(len));
        }
        return buf.toArray();
    }

    String readTextStringKey() {
        return readTextStringKey(in.getByte());
    }

    String readTextStringKey(byte initial) {
        int major = (initial & 0xff) >> 5;
        int additional = initial & 0x1f;
        if (major != 3) {
            in.fail("expected text string key in CBOR map, got major type " + major);
            return "";
        }
        byte[] data = readBytes(3, additional);
        return Utf8Codec.decode(data, 0, data.length);
    }

    static final byte BREAK = (byte) 0xff;

    void decodeValue(Inserter inserter) {
        decodeValue(inserter, in.getByte());
    }

    void decodeValue(Inserter inserter, byte initial) {
        int major = (initial & 0xff) >> 5;
        int additional = initial & 0x1f;

        Cursor cursor;
        switch (major) {
        case 0: { // unsigned integer
            long arg = readArgument(additional);
            if (arg < 0) {
                in.fail("CBOR unsigned integer overflow");
                cursor = inserter.insertNIX();
            } else {
                cursor = inserter.insertLONG(arg);
            }
            break;
        }
        case 1: { // negative integer: value is -1 - argument
            long arg = readArgument(additional);
            if (arg < 0) {
                in.fail("CBOR negative integer overflow");
                cursor = inserter.insertNIX();
            } else {
                cursor = inserter.insertLONG(-1 - arg);
            }
            break;
        }
        case 2: { // byte string -> DATA
            cursor = inserter.insertDATA(readBytes(2, additional));
            break;
        }
        case 3: { // text string -> STRING
            cursor = inserter.insertSTRING(readBytes(3, additional));
            break;
        }
        case 4: { // array
            cursor = inserter.insertARRAY();
            if (additional == 31) {
                for (;;) {
                    byte next = in.getByte();
                    if (in.failed() || next == BREAK) break;
                    decodeValue(arrayInserter.adjust(cursor), next);
                }
            } else {
                int count = (int) readArgument(additional);
                for (int i = 0; i < count; i++) {
                    decodeValue(arrayInserter.adjust(cursor));
                }
            }
            break;
        }
        case 5: { // map -> OBJECT (keys must be text strings)
            cursor = inserter.insertOBJECT();
            if (additional == 31) {
                for (;;) {
                    byte next = in.getByte();
                    if (in.failed() || next == BREAK) break;
                    String key = readTextStringKey(next);
                    decodeValue(objectInserter.adjust(cursor, key));
                }
            } else {
                int count = (int) readArgument(additional);
                for (int i = 0; i < count; i++) {
                    String key = readTextStringKey();
                    decodeValue(objectInserter.adjust(cursor, key));
                }
            }
            break;
        }
        case 6: // tag: skip tag number, decode inner value
            readArgument(additional);
            decodeValue(inserter);
            return;
        case 7: // simple values and floats
            cursor = decodeSimpleOrFloat(inserter, additional);
            break;
        default:
            in.fail("unsupported CBOR major type: " + major);
            cursor = inserter.insertNIX();
            break;
        }
        if (!cursor.valid()) {
            in.fail("failed to decode CBOR value");
        }
    }

    Cursor decodeSimpleOrFloat(Inserter inserter, int additional) {
        return switch (additional) {
            case 20 -> inserter.insertBOOL(false);
            case 21 -> inserter.insertBOOL(true);
            case 22 -> inserter.insertNIX(); // null
            case 23 -> inserter.insertNIX(); // undefined
            case 25 -> inserter.insertDOUBLE(halfToDouble((int) readUint(2)));
            case 26 -> inserter.insertDOUBLE(Float.intBitsToFloat((int) readUint(4)));
            case 27 -> inserter.insertDOUBLE(Double.longBitsToDouble(readUint(8)));
            default -> {
                in.fail("unsupported CBOR simple value: " + additional);
                yield inserter.insertNIX();
            }
        };
    }

    static double halfToDouble(int half) {
        int sign = (half >> 15) & 1;
        int exponent = (half >> 10) & 0x1f;
        int mantissa = half & 0x3ff;

        double value;
        if (exponent == 0) {
            // zero or subnormal: value = 2^-14 * (mantissa / 1024) = mantissa * 2^-24
            value = Math.scalb((double) mantissa, -24);
        } else if (exponent == 31) {
            // infinity or NaN
            value = (mantissa == 0) ? Double.POSITIVE_INFINITY : Double.NaN;
        } else {
            // normal: value = 2^(exp-15) * (1 + mantissa/1024) = (1024 + mantissa) * 2^(exp-25)
            value = Math.scalb((double) (mantissa + 1024), exponent - 25);
        }
        return (sign != 0) ? -value : value;
    }
}
