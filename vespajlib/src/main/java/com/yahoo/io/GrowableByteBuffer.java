// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import com.yahoo.text.Utf8;

import java.nio.*;

/**
 * GrowableByteBuffer encapsulates a ByteBuffer and grows it as needed.
 * The implementation is safe and simple (and certainly a bit inefficient)
 *  - when growing the buffer a new buffer
 * is allocated, the old contents are copied into the new buffer,
 * and the new buffer's position is set to the position of the old
 * buffer.
 * It is possible to set a growth factor. The default is 2.0, meaning that
 * the buffer will double its size when growing.
 *
 * Note that NO methods are re-implemented (except growing the buffer,
 * of course), all are delegated to the encapsulated ByteBuffer.
 * This also includes toString(), hashCode(), equals() and compareTo().
 *
 * No methods except getByteBuffer() expose the encapsulated
 * ByteBuffer, which is intentional.
 *
 * @author Einar M R Rosenvinge
 */
public class GrowableByteBuffer implements Comparable<GrowableByteBuffer> {

    public static final int DEFAULT_BASE_SIZE = 64*1024;
    public static final float DEFAULT_GROW_FACTOR = 2.0f;
    private ByteBuffer buffer;
    private float growFactor;
    private int mark = -1;

    // NOTE: It might have been better to subclass HeapByteBuffer,
    // but that class is package-private. Subclassing ByteBuffer would involve
    // implementing a lot of abstract methods, which would mean reinventing
    // some (too many) wheels.

    // CONSTRUCTORS:

    public GrowableByteBuffer() {
        this(DEFAULT_BASE_SIZE, DEFAULT_GROW_FACTOR);
    }

    public GrowableByteBuffer(int baseSize, float growFactor) {
        setGrowFactor(growFactor);
        //NOTE: We MUST NEVER have a base size of 0, since checkAndGrow() will go into an infinite loop then
        if (baseSize < 16) baseSize = 16;
        buffer = ByteBuffer.allocate(baseSize);
    }

    public GrowableByteBuffer(int baseSize) {
        this(baseSize, DEFAULT_GROW_FACTOR);
    }

    public GrowableByteBuffer(ByteBuffer buffer) {
        this(buffer, DEFAULT_GROW_FACTOR);
    }

    public GrowableByteBuffer(ByteBuffer buffer, float growFactor) {
        this.buffer = buffer;
        setGrowFactor(growFactor);
    }


    // ACCESSORS:

    public float getGrowFactor() {
        return growFactor;
    }

    public void setGrowFactor(float growFactor) {
        if (growFactor <= 1.00f) {
            throw new IllegalArgumentException("Growth factor must be greater than 1.00f, otherwise buffer will never grow!");
        }
        this.growFactor = growFactor;
    }

    public ByteBuffer getByteBuffer() {
        return buffer;
    }

    //PRIVATE GROWTH METHODS

    //TODO: Implement more efficient buffer growth
    //Allocating a new buffer and copying the old buffer into the new one
    //is a simple and uncomplicated strategy.
    //For performance, it would be much better to have a linked list of
    //ByteBuffers and keep track of global position etc., much like
    //GrowableBufferOutputStream does it.

    public void grow(int newSize) {
        //create new buffer:
        ByteBuffer newByteBuf;
        if (buffer.isDirect()) {
            newByteBuf = ByteBuffer.allocateDirect(newSize);
        } else {
            newByteBuf = ByteBuffer.allocate(newSize);
        }
        //set same byte order:
        newByteBuf.order(buffer.order());

        //copy old contents and set correct position:
        int oldPos = buffer.position();
        newByteBuf.position(0);
        buffer.flip();
        newByteBuf.put(buffer);
        newByteBuf.position(oldPos);

        //set same mark:
        if (mark >= 0) {
            newByteBuf.position(mark);
            newByteBuf.mark();
            newByteBuf.position(oldPos);
        }

        //NOTE: No need to preserve "read-only" property,
        //since a read-only buffer cannot grow and will never
        //reach this point anyway

        //NOTE: No need to preserve "limit" property, it would be
        //pointless to grow then...

        //set new buffer to be our buffer:
        buffer = newByteBuf;
    }

    private void accomodate(int putSize) {
        int bufPos = buffer.position();
        int bufSize = buffer.capacity();
        int bufRem = bufSize - bufPos;

        if (bufRem >= putSize) return;

        while (bufRem < putSize) {
            bufSize = (int) ((((float) bufSize) * growFactor) + 100.0);
            bufRem = bufSize - bufPos;
        }

        grow(bufSize);
    }

    //VESPA-ENCODED INTEGERS:

    /**
     * Writes a 62-bit positive integer to the buffer, using 2, 4, or 8 bytes.
     *
     * @param number the integer to write
     */
    public void putInt2_4_8Bytes(long number) {
        if (number < 0L) {
            throw new IllegalArgumentException("Cannot encode negative number.");
        } else if (number > 0x3FFFFFFFFFFFFFFFL) {
            throw new IllegalArgumentException("Cannot encode number larger than 2^62.");
        }

        if (number < 0x8000L) {
            //length 2 bytes
            putShort((short) number);
        } else if (number < 0x40000000L) {
            //length 4 bytes
            putInt(((int) number) | 0x80000000);
        } else {
            //length 8 bytes
            putLong(number | 0xC000000000000000L);
        }
    }

    /**
     * Writes a 32 bit positive integer (or 31 bit unsigned) to the buffer,
     * using 4 bytes.
     *
     * @param number the integer to write
     */
    public void putInt2_4_8BytesAs4(long number) {
        if (number < 0L) {
            throw new IllegalArgumentException("Cannot encode negative number.");
        } else if (number > 0x7FFFFFFFL) {
            throw new IllegalArgumentException("Cannot encode number larger than 2^31-1.");
        }
        putInt(((int) number) | 0x80000000);
    }

    /**
     * Reads a 62-bit positive integer from the buffer, which was written using 2, 4, or 8 bytes.
     *
     * @return the integer read
     */
    public long getInt2_4_8Bytes() {
        byte flagByte = get();
        position(position() - 1);

        if ((flagByte & 0x80) != 0) {
            if ((flagByte & 0x40) != 0) {
                //length 8 bytes
                return getLong() & 0x3FFFFFFFFFFFFFFFL;
            } else {
                //length 4 bytes
                return getInt() & 0x3FFFFFFF;
            }
        } else {
            //length 2 bytes
            return getShort();
        }
    }

    /**
     * Computes the size used for storing the given integer using 2, 4 or 8 bytes.
     *
     * @param number the integer to check length of
     * @return the number of bytes used to store it; 2, 4 or 8
     */
    public static int getSerializedSize2_4_8Bytes(long number) {
        if (number < 0L) {
            throw new IllegalArgumentException("Cannot encode negative number.");
        } else if (number > 0x3FFFFFFFFFFFFFFFL) {
            throw new IllegalArgumentException("Cannot encode number larger than 2^62.");
        }

        if (number < 0x8000L) {
            //length 2 bytes
            return 2;
        } else if (number < 0x40000000L) {
            //length 4 bytes
            return 4;
        } else {
            //length 8 bytes
            return 8;
        }
    }

    /**
     * Writes a 30-bit positive integer to the buffer, using 1, 2, or 4 bytes.
     *
     * @param number the integer to write
     */
    public void putInt1_2_4Bytes(int number) {
        if (number < 0) {
            throw new IllegalArgumentException("Cannot encode negative number");
        } else if (number > 0x3FFFFFFF) {
            throw new IllegalArgumentException("Cannot encode number larger than 2^30.");
        }

        if (number < 0x80) {
            //length 1 byte
            put((byte) number);
        } else if (number < 0x4000) {
            //length 2 bytes
            putShort((short) (((short)number) | ((short) 0x8000)));
        } else {
            //length 4 bytes
            putInt(number | 0xC0000000);
        }
    }

    /**
     * Writes a 30-bit positive integer to the buffer, using 4 bytes.
     *
     * @param number the integer to write
     */
    public void putInt1_2_4BytesAs4(int number) {
        if (number < 0) {
            throw new IllegalArgumentException("Cannot encode negative number");
        } else if (number > 0x3FFFFFFF) {
            throw new IllegalArgumentException("Cannot encode number larger than 2^30.");
        }
        putInt(number | 0xC0000000);
    }

    /**
     * Reads a 30-bit positive integer from the buffer, which was written using 1, 2, or 4 bytes.
     *
     * @return the integer read
     */
     public int getInt1_2_4Bytes() {
        byte flagByte = get();
        position(position() - 1);

        if ((flagByte & 0x80) != 0) {
            if ((flagByte & 0x40) != 0) {
                //length 4 bytes
                return getInt() & 0x3FFFFFFF;
            } else {
                //length 2 bytes
                return getShort() & 0x3FFF;
            }
        } else {
            //length 1 byte
            return get();
        }
    }

    /**
     * Computes the size used for storing the given integer using 1, 2 or 4 bytes.
     *
     * @param number the integer to check length of
     * @return the number of bytes used to store it; 1, 2 or 4
     */
    public static int getSerializedSize1_2_4Bytes(int number) {
        if (number < 0) {
            throw new IllegalArgumentException("Cannot encode negative number");
        } else if (number > 0x3FFFFFFF) {
            throw new IllegalArgumentException("Cannot encode number larger than 2^30.");
        }

        if (number < 0x80) {
            //length 1 byte
            return 1;
        } else if (number < 0x4000) {
            //length 2 bytes
            return 2;
        } else {
            //length 4 bytes
            return 4;
        }
    }

    /**
     * Writes a 31-bit positive integer to the buffer, using 1 or 4 bytes.
     *
     * @param number the integer to write
     */
    public void putInt1_4Bytes(int number) {
        if (number < 0) {
            throw new IllegalArgumentException("Cannot encode negative number");
        }
        //no need to check upper boundary, since INT_MAX == 2^31

        if (number < 0x80) {
            //length 1 byte
            put((byte) number);
        } else {
            //length 4 bytes
            putInt(number | 0x80000000);
        }
    }

    /**
     * Writes a 31-bit positive integer to the buffer, using 4 bytes.
     *
     * @param number the integer to write
     */
    public void putInt1_4BytesAs4(int number) {
        if (number < 0) {
            throw new IllegalArgumentException("Cannot encode negative number");
        }
        //no need to check upper boundary, since INT_MAX == 2^31
        putInt(number | 0x80000000);
    }

    /**
     * Reads a 31-bit positive integer from the buffer, which was written using 1 or 4 bytes.
     *
     * @return the integer read
     */
    public int getInt1_4Bytes() {
        byte flagByte = get();
        position(position() - 1);

        if ((flagByte & 0x80) != 0) {
            //length 4 bytes
            return getInt() & 0x7FFFFFFF;
        } else {
            //length 1 byte
            return get();
        }
    }

    /** Writes this string to the buffer as a 1_4 encoded length in bytes followed by the utf8 bytes */
    public void putUtf8String(String value) {
        byte[] stringBytes = Utf8.toBytes(value);
        putInt1_4Bytes(stringBytes.length);
        put(stringBytes);
    }

    /** Reads a string from the buffer as a 1_4 encoded length in bytes followed by the utf8 bytes */
    public String getUtf8String() {
        int stringLength = getInt1_4Bytes();
        byte[] stringBytes = new byte[stringLength];
        get(stringBytes);
        return Utf8.toString(stringBytes);
    }

    /**
     * Computes the size used for storing the given integer using 1 or 4 bytes.
     *
     * @param number the integer to check length of
     * @return the number of bytes used to store it; 1 or 4
     */
    public static int getSerializedSize1_4Bytes(int number) {
        if (number < 0) {
            throw new IllegalArgumentException("Cannot encode negative number");
        }
        //no need to check upper boundary, since INT_MAX == 2^31

        if (number < 0x80) {
            //length 1 byte
            return 1;
        } else {
            //length 4 bytes
            return 4;
        }
    }

    //METHODS OF ENCAPSULATED BYTEBUFFER:
    public static GrowableByteBuffer allocate(int capacity) {
        return new GrowableByteBuffer(ByteBuffer.allocate(capacity));
    }
    public static GrowableByteBuffer allocate(int capacity, float growFactor) {
        return new GrowableByteBuffer(ByteBuffer.allocate(capacity), growFactor);
    }
    public static GrowableByteBuffer allocateDirect(int capacity) {
        return new GrowableByteBuffer(ByteBuffer.allocateDirect(capacity));
    }
    public static GrowableByteBuffer allocateDirect(int capacity, float growFactor) {
        return new GrowableByteBuffer(ByteBuffer.allocateDirect(capacity), growFactor);
    }
    public final byte[] array() {
        return buffer.array();
    }
    public final int arrayOffset() {
        return buffer.arrayOffset();
    }
    public CharBuffer asCharBuffer() {
        return buffer.asCharBuffer();
    }
    public DoubleBuffer asDoubleBuffer() {
        return buffer.asDoubleBuffer();
    }
    public FloatBuffer asFloatBuffer() {
        return buffer.asFloatBuffer();
    }
    public IntBuffer asIntBuffer() {
        return buffer.asIntBuffer();
    }
    public LongBuffer asLongBuffer() {
        return buffer.asLongBuffer();
    }
    public GrowableByteBuffer asReadOnlyBuffer() {
        return new GrowableByteBuffer(buffer.asReadOnlyBuffer(), growFactor);
    }
    public ShortBuffer asShortBuffer() {
        return buffer.asShortBuffer();
    }
    public GrowableByteBuffer compact() {
        buffer.compact();
        return this;
    }
    public int compareTo(GrowableByteBuffer that) {
        return buffer.compareTo(that.buffer);
    }
    public GrowableByteBuffer duplicate() {
        return new GrowableByteBuffer(buffer.duplicate(), growFactor);
    }
    public boolean equals(Object obj) {
        if (!(obj instanceof GrowableByteBuffer)) {
            return false;
        }
        GrowableByteBuffer rhs = (GrowableByteBuffer)obj;
        if (!buffer.equals(rhs.buffer)) {
            return false;
        }
        return true;
    }
    public byte get() {
        return buffer.get();
    }
    public GrowableByteBuffer get(byte[] dst) {
        buffer.get(dst);
        return this;
    }
    public GrowableByteBuffer get(byte[] dst, int offset, int length) {
        buffer.get(dst, offset, length);
        return this;
    }
    public byte get(int index) {
        return buffer.get(index);
    }
    public char getChar() {
        return buffer.getChar();
    }
    public char getChar(int index) {
        return buffer.getChar(index);
    }
    public double getDouble() {
        return buffer.getDouble();
    }
    public double getDouble(int index) {
        return buffer.getDouble(index);
    }
    public float getFloat() {
        return buffer.getFloat();
    }
    public float getFloat(int index) {
        return buffer.getFloat(index);
    }
    public int getInt() {
        return buffer.getInt();
    }
    public int getInt(int index) {
        return buffer.getInt(index);
    }
    public long getLong() {
        return buffer.getLong();
    }
    public long getLong(int index) {
        return buffer.getLong(index);
    }
    public short getShort() {
        return buffer.getShort();
    }
    public short getShort(int index) {
        return buffer.getShort(index);
    }
    public boolean hasArray() {
        return buffer.hasArray();
    }
    public int hashCode() {
        return buffer.hashCode();
    }
    public boolean isDirect() {
        return buffer.isDirect();
    }
    public ByteOrder order() {
        return buffer.order();
    }
    public GrowableByteBuffer order(ByteOrder bo) {
        buffer.order(bo);
        return this;
    }

    public GrowableByteBuffer put(byte b) {
        try {
            buffer.put(b);
        } catch (BufferOverflowException e) {
            accomodate(1);
            buffer.put(b);
        }
        return this;
    }
    public GrowableByteBuffer put(byte[] src) {

        accomodate(src.length);
        buffer.put(src);
        return this;
    }
    public GrowableByteBuffer put(byte[] src, int offset, int length) {

        accomodate(length);
        buffer.put(src, offset, length);
        return this;
    }
    public GrowableByteBuffer put(ByteBuffer src) {
        accomodate(src.remaining());
        buffer.put(src);
        return this;
    }
    public GrowableByteBuffer put(GrowableByteBuffer src) {

        accomodate(src.remaining());
        buffer.put(src.buffer);
        return this;
    }
    // XXX: the put{Type}(index, value) methods do not handle index > position
    public GrowableByteBuffer put(int index, byte b) {
        try {
            buffer.put(index, b);
        } catch (IndexOutOfBoundsException e) {
            accomodate(1);
            buffer.put(index, b);
        }
        return this;
    }
    public GrowableByteBuffer putChar(char value) {
        try {
            buffer.putChar(value);
        } catch (BufferOverflowException e) {
            accomodate(2);
            buffer.putChar(value);
        }
        return this;
    }
    public GrowableByteBuffer putChar(int index, char value) {
        try {
            buffer.putChar(index, value);
        } catch (IndexOutOfBoundsException e) {
            accomodate(2);
            buffer.putChar(index, value);
        }
        return this;
    }
    public GrowableByteBuffer putDouble(double value) {
        try {
            buffer.putDouble(value);
        } catch (BufferOverflowException e) {
            accomodate(8);
            buffer.putDouble(value);
        }
        return this;
    }
    public GrowableByteBuffer putDouble(int index, double value) {
        try {
            buffer.putDouble(index, value);
        } catch (IndexOutOfBoundsException e) {
            accomodate(8);
            buffer.putDouble(index, value);
        }
        return this;
    }
    public GrowableByteBuffer putFloat(float value) {
        try {
            buffer.putFloat(value);
        } catch (BufferOverflowException e) {
            accomodate(4);
            buffer.putFloat(value);
        }
        return this;
    }
    public GrowableByteBuffer putFloat(int index, float value) {
        try {
            buffer.putFloat(index, value);
        } catch (IndexOutOfBoundsException e) {
            accomodate(4);
            buffer.putFloat(index, value);
        }
        return this;
    }
    public GrowableByteBuffer putInt(int value) {
        try {
            buffer.putInt(value);
        } catch (BufferOverflowException e) {
            accomodate(4);
            buffer.putInt(value);
        }
        return this;
    }
    public GrowableByteBuffer putInt(int index, int value) {
        try {
            buffer.putInt(index, value);
        } catch (IndexOutOfBoundsException e) {
            accomodate(4);
            buffer.putInt(index, value);
        }
        return this;
    }
    public GrowableByteBuffer putLong(int index, long value) {
        try {
            buffer.putLong(index, value);
        } catch (IndexOutOfBoundsException e) {
            accomodate(8);
            buffer.putLong(index, value);
        }
        return this;
    }
    public GrowableByteBuffer putLong(long value) {
        try {
            buffer.putLong(value);
        } catch (BufferOverflowException e) {
            accomodate(8);
            buffer.putLong(value);
        }
        return this;
    }
    public GrowableByteBuffer putShort(int index, short value) {
        try {
            buffer.putShort(index, value);
        } catch (IndexOutOfBoundsException e) {
            accomodate(2);
            buffer.putShort(index, value);
        }
        return this;
    }
    public GrowableByteBuffer putShort(short value) {
        try {
            buffer.putShort(value);
        } catch (BufferOverflowException e) {
            accomodate(2);
            buffer.putShort(value);
        }
        return this;
    }

    /**
     * Behaves as ByteBuffer slicing, but the internal buffer will no longer be
     * shared if one of the buffers is forced to grow.
     *
     * @return a new buffer with shared contents
     * @see ByteBuffer#slice()
     */
    public GrowableByteBuffer slice() {
        ByteBuffer b = buffer.slice();
        return new GrowableByteBuffer(b, growFactor);
    }

    public String toString() {
        return "GrowableByteBuffer"
                + "[pos="+ position()
                + " lim=" + limit()
                + " cap=" + capacity()
                + " grow=" + growFactor
                + "]";
    }
    public static GrowableByteBuffer wrap(byte[] array) {
        return new GrowableByteBuffer(ByteBuffer.wrap(array));
    }
    public static GrowableByteBuffer wrap(byte[] array, float growFactor) {
        return new GrowableByteBuffer(ByteBuffer.wrap(array), growFactor);
    }
    public static GrowableByteBuffer wrap(byte[] array, int offset, int length) {
        return new GrowableByteBuffer(ByteBuffer.wrap(array, offset, length));
    }
    public static GrowableByteBuffer wrap(byte[] array, int offset, int length, float growFactor) {
        return new GrowableByteBuffer(ByteBuffer.wrap(array, offset, length), growFactor);
    }

    //METHODS FROM ENCAPSULATED BUFFER:

    public final int capacity() {
        return buffer.capacity();
    }
    public final void clear() {
        buffer.clear();
        mark = -1;
    }
    public final void flip() {
        buffer.flip();
        mark = -1;
    }
    public final boolean hasRemaining() {
        return buffer.hasRemaining();
    }
    public final boolean isReadOnly() {
        return buffer.isReadOnly();
    }
    public final int limit() {
        return buffer.limit();
    }
    public final void limit(int newLimit) {
        buffer.limit(newLimit);
        if (mark >  newLimit) mark = -1;
    }
    public final void mark() {
        buffer.mark();
        mark = position();
    }
    public final int position() {
        return buffer.position();
    }
    public final void position(int newPosition) {
        buffer.position(newPosition);
        if (mark >  newPosition) mark = -1;
    }
    public final int remaining() {
        return buffer.remaining();
    }
    public final void reset() {
        buffer.reset();
    }
    public final void rewind() {
        buffer.rewind();
        mark = -1;
    }
}
