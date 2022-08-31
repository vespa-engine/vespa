// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Utility class with functions for handling UTF-8
 *
 * @author arnej27959
 * @author Steinar Knutsen
 * @author baldersheim
 */
public final class Utf8 {

    private static final byte [] TRUE = {(byte) 't', (byte) 'r', (byte) 'u', (byte) 'e'};
    private static final byte [] FALSE = {(byte) 'f', (byte) 'a', (byte) 'l', (byte) 's', (byte) 'e'};
    private static final byte[] LONG_MIN_VALUE_BYTES = String.valueOf(Long.MIN_VALUE).getBytes(StandardCharsets.UTF_8);

    /** Returns the Charset instance for UTF-8 */
    public static Charset getCharset() {
        return StandardCharsets.UTF_8;
    }

    /** To be used instead of String.String(byte[] bytes) */
    public static String toStringStd(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Utility method as toString(byte[]).
     *
     * @param data
     *            bytes to decode
     * @param offset
     *            index of first byte to decode
     * @param length
     *            number of bytes to decode
     * @return String decoded from UTF-8
     */
    public static String toString(byte[] data, int offset, int length) {
        return toString(ByteBuffer.wrap(data, offset, length));
    }

    /**
     * Fetch a string from a ByteBuffer instance. ByteBuffer instances are
     * stateful, so it is assumed to caller manipulates the instance's limit if
     * the entire buffer is not a string.
     *
     * @param data
     *            The UTF-8 data source
     * @return a decoded String
     */
    public static String toString(ByteBuffer data) {
        CharBuffer c = StandardCharsets.UTF_8.decode(data);
        return c.toString();
    }

    /**
     * Uses String.getBytes directly.
     */
    public static byte[] toBytesStd(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encode a long as its decimal representation, i.e. toAsciiBytes(15L) will
     * return "15" encoded as UTF-8. In other words it is an optimized version
     * of String.valueOf() followed by UTF-8 encoding. Avoid going through
     * string in order to get a simple UTF-8 sequence.
     *
     * @param l
     *            value to represent as a decimal number encded as utf8
     * @return byte array
     */
    public static byte[] toAsciiBytes(long l) {
        // Handle Long.MIN_VALUE specifically, since it breaks all the assumptions
        if (Long.MIN_VALUE == l) {
            return LONG_MIN_VALUE_BYTES;
        }
        int count=1;
        for (long v= l<0 ? -l : l; v >= 10; v=v/10, count++);
        byte [] buf = new byte [count + ((l<0) ? 1 : 0)];
        int offset = 0;
        if (l < 0) {
            buf[offset++] = (byte) '-';
            l = -l;
        }
        for (count--; count >= 0; l=l/10, count--) {
            buf[count+offset] = (byte)(0x30 + l%10);
        }
        return buf;
    }

    public static byte [] toAsciiBytes(boolean v) {
        return v ? TRUE : FALSE;
    }

    /**
     * Encode a UTF-8 string.
     *
     * @param string The string to encode.
     * @return Utf8 encoded array
     */
    public static byte[] toBytes(String string) {
        // This is just wrapper for String::getBytes. Pre-Java 9 this had a more efficient approach for ASCII-only strings.
        return string.getBytes(StandardCharsets.UTF_8);
    }
    /**
     * Decode a UTF-8 string.
     *
     * @param utf8 the bytes to decode
     * @return Utf8 encoded array
     */
    public static String toString(byte[] utf8) {
        // This is just wrapper for String::new. Pre-Java 9 this had a more efficient approach for ASCII-onlu strings.
        return new String(utf8, StandardCharsets.UTF_8);
    }

    /**
     * Utility method as toBytes(String).
     *
     * @param str
     *            String to encode
     * @param offset
     *            index of first character to encode
     * @param length
     *            number of characters to encode
     * @return substring encoded as UTF-8
     */
    public static byte[] toBytes(String str, int offset, int length) {
        CharBuffer c = CharBuffer.wrap(str, offset, offset + length);
        ByteBuffer b = StandardCharsets.UTF_8.encode(c);
        byte[] result = new byte[b.remaining()];
        b.get(result);
        return result;
    }

    /**
     * Direct encoding of a String into an array.
     *
     * @param str
     *            string to encode
     * @param srcOffset
     *            index of first character in string to encode
     * @param srcLen
     *            number of characters in string to encode
     * @param dst
     *            destination for encoded data
     * @param dstOffset
     *            index of first position to write data
     * @return the number of bytes written to the array.
     */
    public static int toBytes(String str, int srcOffset, int srcLen, byte[] dst, int dstOffset) {
        CharBuffer c = CharBuffer.wrap(str, srcOffset, srcOffset + srcLen);
        ByteBuffer b = StandardCharsets.UTF_8.encode(c);
        int encoded = b.remaining();
        b.get(dst, dstOffset, encoded);
        return encoded;
    }

    /**
     * Encode a string directly into a ByteBuffer instance.
     *
     * <p>
     * This method is somewhat more cumbersome than the rest of the helper
     * methods in this library, as it is intended for use cases in the following
     * style, if extraneous copying is highly undesirable:
     *
     * <pre>
     * String[] a = {"abc", "def", "ghi\u00e8"};
     * int[] aLens = {3, 3, 5};
     * CharsetEncoder ce = Utf8.getNewEncoder();
     * ByteBuffer forWire = ByteBuffer.allocate(someNumber);
     *
     * for (int i = 0; i &lt; a.length; i++) {
     *     forWire.putInt(aLens[i]);
     *     Utf8.toBytes(a[i], 0, a[i].length(), forWire, ce);
     * }
     * </pre>
     *
     * @see Utf8#getNewEncoder()
     *
     * @param src the string to encode
     * @param srcOffset index of first character to encode
     * @param srcLen number of characters to encode
     * @param dst the destination ByteBuffer
     * @param encoder the character encoder to use
     */
    public static void toBytes(String src, int srcOffset, int srcLen, ByteBuffer dst, CharsetEncoder encoder) {
        CharBuffer c = CharBuffer.wrap(src, srcOffset, srcOffset + srcLen);
        encoder.encode(c, dst, true);
    }

    /**
     * Create a new UTF-8 encoder.
     *
     * @see Utf8#toBytes(String, int, int, ByteBuffer, CharsetEncoder)
     */
    public static CharsetEncoder getNewEncoder() {
        return StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    /**
     * Count the number of bytes needed to represent a given sequence of 16-bit
     * char values as a UTF-8 encoded array. This method is written to be cheap
     * to invoke.
     *
     * Note: It is strongly assumed to character sequence is valid.
     */
    public static int byteCount(CharSequence str) { return byteCount(str, 0, str.length()); }

    /**
     * Count the number of bytes needed to represent a given sequence of 16-bit
     * char values as a UTF-8 encoded array. This method is written to be cheap
     * to invoke.
     *
     * Note: It is strongly assumed to character sequence is valid.
     */
    public static int byteCount(CharSequence str, int offset, int length) {
        int count = 0;
        int barrier = offset + length;
        int i = offset;
        while (i < barrier) {
            int codePoint = (int) str.charAt(i);
            if (codePoint < 0x800) {
                if (codePoint < 0x80) {
                    ++count;
                } else {
                    count += 2;
                }
                ++i;
            } else {
                // bit masking to check (codePoint >= 0xd800 && codePoint <
                // 0xe000)
                if ((codePoint & 0xF800) == 0xD800) {
                    count += 4;
                    i += 2;
                } else {
                    count += 3;
                    ++i;
                }
            }
        }
        return count;
    }

    /**
     * Count the number of Unicode code units ("UTF-16 characters") needed to
     * represent a given array of UTF-8 characters. This method is written to be
     * cheap to invoke.
     *
     * Note: It is strongly assumed the sequence is valid.
     */
    public static int unitCount(byte[] utf8) { return unitCount(utf8, 0, utf8.length); }

    /**
     * Count the number of Unicode code units ("UTF-16 characters") needed to
     * represent a given array of UTF-8 characters. This method is written to be
     * cheap to invoke.
     *
     * Note: It is strongly assumed the sequence is valid.
     *
     * @param utf8
     *            raw data
     * @param offset
     *            index of first byte of UTF-8 sequence to check
     * @param length
     *            number of bytes in the UTF-8 sequence to check
     */
    public static int unitCount(byte[] utf8, int offset, int length) {
        int units = 0;
        int barrier = offset + length;
        int i = offset;
        while (i < barrier) {
            byte firstByte = utf8[i];
            if (firstByte >= -16) {
                if (firstByte >= 0) {
                    ++units;
                    ++i;
                } else {
                    units += 2;
                    i += 4;
                }
            } else {
                if (firstByte >= -32) {
                    ++units;
                    i += 3;
                } else {
                    ++units;
                    i += 2;
                }
            }
        }
        return units;
    }

    /**
     * Calculate the number of Unicode code units ("UTF-16 characters") needed
     * to represent a given UTF-8 encoded code point.
     *
     * @param firstByte
     *            the first byte of a character encoded as UTF-8
     * @return the number of UTF-16 code units needed to represent the given
     *         code point
     */
    public static int unitCount(byte firstByte) {
        int units = 0;
        if (firstByte >= -16) {
            if (firstByte >= 0) {
                units = 1;
            } else {
                units = 2;
            }
        } else {
            units = 1;
        }
        return units;
    }

    /**
     * Inspects a byte assumed to be the first byte in a UTF8 to check how many
     * bytes in total the sequence of bytes will use.
     *
     * @param firstByte
     *            the first byte of a UTF8 encoded character
     * @return the number of bytes used to encode the character
     */
    // To avoid code duplication, this function should be used by unitCount(),
    // but then unitCount(byte[], int, int) would not be as tight. This class is in general
    // meant to be safe to use in performance sensitive code.
    public static int totalBytes(byte firstByte) {
        if (firstByte >= -16) {
            if (firstByte >= 0) {
                return 1;
            } else {
                return 4;
            }
        } else {
            if (firstByte >= -32) {
                return 3;
            } else {
                return 2;
            }
        }
    }

    /**
     * Returns an integer array the length as the input string plus one. For
     * every index in the array, the corresponding value gives the index into
     * the UTF-8 byte sequence that can be created from the input.
     *
     * @param value
     *            a String to generate UTF-8 byte indexes from
     * @return an array containing corresponding UTF-8 byte indexes
     */
    public static int[] calculateBytePositions(CharSequence value) {
        int[] positions = new int[value.length() + 1];

        int bytePos = 0;
        int barrier = value.length();
        int i = 0;
        int codepointNo = 0;
        positions[codepointNo++] = bytePos;
        while (i < barrier) {
            int codePoint = (int) value.charAt(i);
            if (codePoint < 0x800) {
                if (codePoint < 0x80) {
                    ++bytePos;
                } else {
                    bytePos += 2;
                }
                ++i;
            } else {
                // bit masking to check (codePoint >= 0xd800 && codePoint <
                // 0xe000)
                if ((codePoint & 0xF800) == 0xD800) {
                    // double position write, as we have a surrogate pair
                    positions[codepointNo++] = bytePos;
                    bytePos += 4;
                    i += 2;
                } else {
                    bytePos += 3;
                    ++i;
                }
            }
            positions[codepointNo++] = bytePos;
        }
        return positions;
    }

    /**
     * Returns an array of the same length as the input array plus one. For
     * every index in the array, the corresponding value gives the index into
     * the Java string (UTF-16 sequence) that can be created from the input.
     *
     * @param utf8
     *            a byte array containing a string encoded as UTF-8. Note: It is
     *            strongly assumed that this sequence is correct.
     * @return an array containing corresponding UTF-16 character indexes. If input
     *            array is empty, returns an array containg a single zero.
     */
    public static int[] calculateStringPositions(byte[] utf8) {
        if (utf8.length == 0) {
            return new int[] { 0 };
        }
        int[] positions = new int[utf8.length + 1];
        int utf8BytePos = 0;
        int charPos = 0;
        int lastUtf8SequencePos = 0;
        int utf8SequenceLen = 0;
        while (utf8BytePos < utf8.length) {
            utf8SequenceLen = totalBytes(utf8[utf8BytePos]);
            lastUtf8SequencePos = utf8BytePos;
            for (int utf8SequenceCnt = 0; utf8SequenceCnt < utf8SequenceLen; utf8SequenceCnt++) {
                positions[utf8BytePos + utf8SequenceCnt] = charPos;
            }
            utf8BytePos += utf8SequenceLen;
            charPos++;
        }
        //we need to check if the last UTF-8 sequence resulted in a surrogate pair:
        int lastCharLen = unitCount(utf8, lastUtf8SequencePos, utf8SequenceLen);
        positions[utf8.length] = charPos + lastCharLen - 1;
        return positions;
    }


    /**
     * Encode a valid Unicode codepoint as a sequence of UTF-8 bytes into a new allocated array.
     *
     * @param codepoint Unicode codepoint to encode
     * @return number of bytes written
     * @throws IndexOutOfBoundsException if there is insufficient room for the encoded data in the given array
     */
    public static byte[] encode(int codepoint) {
        byte[] destination = new byte[codePointAsUtf8Length(codepoint)];
        encode(codepoint, destination, 0);
        return destination;
    }

    /**
     * Encode a valid Unicode codepoint as a sequence of UTF-8 bytes into an array.
     *
     * @param codepoint Unicode codepoint to encode
     * @param destination array to write into
     * @param offset index of first byte written
     * @return index of the first byte after the last byte written (i.e. offset plus number of bytes written)
     * @throws IndexOutOfBoundsException if there is insufficient room for the encoded data in the given array
     */
    public static int encode(int codepoint, byte[] destination, int offset) {
        int writeOffset = offset;
        byte firstByte = firstByte(codepoint);
        int leftToWrite = codePointAsUtf8Length(codepoint) - 1;
        destination[writeOffset++] = firstByte;
        while (leftToWrite-- > 0) {
            destination[writeOffset++] = trailingOctet(codepoint, leftToWrite);
        }
        return writeOffset;
    }

    /**
     * Encode a valid Unicode codepoint as a sequence of UTF-8 bytes into a
     * ByteBuffer.
     *
     * @param codepoint
     *            Unicode codepoint to encode
     * @param destination
     *            buffer to write into
     * @throws BufferOverflowException
     *             if the buffer's limit is met while writing (propagated from
     *             the ByteBuffer)
     * @throws ReadOnlyBufferException
     *             if the buffer is read only (propagated from the ByteBuffer)
     */
    public static void encode(int codepoint, ByteBuffer destination) {
        byte firstByte = firstByte(codepoint);
        int leftToWrite = codePointAsUtf8Length(codepoint) - 1;
        destination.put(firstByte);
        while (leftToWrite-- > 0) {
            destination.put(trailingOctet(codepoint, leftToWrite));
        }
    }

    /**
     * Encode a valid Unicode codepoint as a sequence of UTF-8 bytes into an
     * OutputStream.
     *
     * @param codepoint
     *            Unicode codepoint to encode
     * @param destination
     *            buffer to write into
     * @return number of bytes written
     * @throws IOException
     *             propagated from stream
     */
    public static int encode(int codepoint, OutputStream destination) throws IOException {
        byte firstByte = firstByte(codepoint);
        int toWrite = codePointAsUtf8Length(codepoint);
        int leftToWrite = toWrite - 1;
        destination.write(firstByte);
        while (leftToWrite-- > 0) {
            destination.write(trailingOctet(codepoint, leftToWrite));
        }
        return toWrite;
    }


    private static byte trailingOctet(int codepoint, int leftToWrite) {
        return (byte) (0x80 | ((codepoint >> (6 * leftToWrite)) & 0x3F));
    }

    private static byte firstByte(int codepoint) {
        if (codepoint < 0x800) {
            if (codepoint < 0x80) {
                return (byte) codepoint;
            } else {
                return (byte) (0xC0 | codepoint >> 6);
            }
        } else {
            if (codepoint < 0x10000) {
                return (byte) (0xE0 | codepoint >> 12);
            } else {
                return (byte) (0xF0 | codepoint >> 18);
            }
        }

    }

    /**
     * Return the number of octets needed to encode a valid Unicode codepoint as UTF-8.
     *
     * @param codepoint the Unicode codepoint to inspect
     * @return the number of bytes needed for UTF-8 representation
     */
    public static int codePointAsUtf8Length(int codepoint) {
        if (codepoint < 0x800) {
            if (codepoint < 0x80) {
                return 1;
            } else {
                return 2;
            }
        } else {
            if (codepoint < 0x10000) {
                return 3;
            } else {
                return 4;
            }
        }
    }

}
