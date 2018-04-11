// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.log.LogLevel;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Superclass of fs4 packets
 *
 * @author bratseth
 */
public abstract class BasicPacket {

    private final Compressor compressor = new Compressor();

    private static Logger log = Logger.getLogger(QueryResultPacket.class.getName());
    private static int DEFAULT_WRITE_BUFFER_SIZE = (10 * 1024);
    public static final int CODE_MASK = 0x00ff_ffff;  // Reserve upper byte for flags.

    protected byte[] encodedBody;

    protected ByteBuffer encodingBuffer;

    /** The length of this packet in bytes or -1 if not known */
    protected int length = -1;

    /**
     * A timestamp which can be set or inspected by clients of this class
     * but which is never updated by the class itself.  This is mostly
     * a convenience for when you need to queue packets or retain them
     * in some structure where their validity is limited by a timeout
     * or similar.
     */
    private long timeStamp = -1;

    private int compressionLimit = 0;

    private CompressionType compressionType;

    /**
     * Sets the number of bytes the package must be before activating compression.
     * A value of 0 means no compression.
     *
     * @param limit smallest package size that triggers compression.
     */
    public void setCompressionLimit(int limit) { compressionLimit = limit; }

    public void setCompressionType(String type) {
        compressionType = CompressionType.valueOf(type);
    }

    /**
     * Fills this package from a byte buffer positioned at the first byte of the package
     *
     * @return this for convenience
     * @throws UnsupportedOperationException if not implemented in the subclass
     */
    public BasicPacket decode(ByteBuffer buffer) {
        length = buffer.getInt()+4; // Streamed packet length is the length-4
        int code = buffer.getInt();

        decodeAndDecompressBody(buffer, code, length - 2*4);
        return this;
    }

    protected void decodeAndDecompressBody(ByteBuffer buffer, int code, int packetLength) {
        byte compressionType = (byte)((code & ~CODE_MASK) >> 24);
        boolean isCompressed = compressionType != 0;
        codeDecodedHook(code & CODE_MASK);
        if (isCompressed) {
            int uncompressedSize = buffer.getInt();
            int compressedSize = packetLength - 4;
            int offset = 0;
            byte[] compressedData;
            if (buffer.hasArray()) {
                compressedData = buffer.array();
                offset = buffer.arrayOffset() + buffer.position();
                buffer.position(buffer.position() + compressedSize);
            } else {
                compressedData = new byte[compressedSize];
                buffer.get(compressedData);
            }
            byte[] body = compressor.decompress(CompressionType.valueOf(compressionType), compressedData, offset,
                                                uncompressedSize, Optional.of(compressedSize));
            ByteBuffer bodyBuffer = ByteBuffer.wrap(body);
            length += uncompressedSize - (compressedSize + 4);
            decodeBody(bodyBuffer);
        } else {
            decodeBody(buffer);
        }
    }

    /**
     * Decodes the body of this package from a byte buffer
     * positioned at the first byte of the package.
     *
     * @throws UnsupportedOperationException if not implemented in the subclass
     */
    public void decodeBody(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Decoding of " + this + " is not implemented");
    }

    /**
     * Called when the packet code is decoded.
     * This default implementation just throws an exception if the code
     * is not the code of this packet. Packets which has several possible codes
     * will use this method to store the code.
     */
    protected void codeDecodedHook(int code) {
        if (code != getCode())
            throw new RuntimeException("Can not decode " + code + " into " + this);
    }

    /**
     * <p>Encodes this package onto the given buffer at the current position.
     * The position of the buffer after encoding is the byte following
     * the last encoded byte.</p>
     *
     * <p>This method will ensure that everything is written provided
     * sufficient capacity regardless of the buffer limit.
     * When returning, the limit is at the end of the package (qual to the
     * position).</p>
     *
     * @return this for convenience
     * @throws UnsupportedOperationException if not implemented in the subclass
     */
    public BasicPacket encode(ByteBuffer buffer) throws BufferTooSmallException {
        int oldLimit = buffer.limit();
        int startPosition = buffer.position();

        buffer.limit(buffer.capacity());
        try {
            buffer.putInt(4); // Real length written later, when we know it
            buffer.putInt(getCode());

            encodeAndCompressBody(buffer, startPosition);
        }
        catch (java.nio.BufferOverflowException e) {
            // reset buffer to expected state
            buffer.position(startPosition);
            buffer.limit(oldLimit);
            throw new BufferTooSmallException("Destination buffer too small while encoding packet");
        }

        return this;
    }

    protected void encodeAndCompressBody(ByteBuffer buffer, int startPosition) {
        int startOfBody = buffer.position();
        encodeBody(buffer);
        setEncodedBody(buffer, startOfBody, buffer.position() - startOfBody);
        length = buffer.position() - startPosition;

        if (compressionLimit != 0 && length-4 > compressionLimit) {
            byte[] compressedBody;
            compressionType = CompressionType.LZ4;
            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4Compressor compressor = factory.fastCompressor();
            compressedBody = compressor.compress(encodedBody);

            log.log(LogLevel.DEBUG, "Uncompressed size: " + encodedBody.length + ", Compressed size: " + compressedBody.length);
            if (compressedBody.length + 4 < encodedBody.length) {
                buffer.position(startPosition);
                buffer.putInt(compressedBody.length + startOfBody - startPosition + 4 - 4);  // +4 for compressed size
                buffer.putInt(getCompressedCode(compressionType));
                buffer.position(startOfBody);
                buffer.putInt(encodedBody.length);
                buffer.put(compressedBody);
                buffer.limit(buffer.position());
                return;
            }
        }
        buffer.putInt(startPosition, length - 4); // Encoded length 4 less than actual length
        buffer.limit(buffer.position());
    }

    private int getCompressedCode(CompressionType compression) {
        int code = compression.getCode();
        return getCode() | (code << 24);
    }

    /**
     * Encodes the body of this package onto the given buffer at the current position.
     * The position of the buffer after encoding is the byte following
     * the last encoded byte.
     *
     * @throws UnsupportedOperationException if not implemented in the subclass
     */
    protected void encodeBody(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Encoding of " + this + " is not implemented");
    }

    protected void setEncodedBody(ByteBuffer b, int start, int length) {
        encodedBody = new byte[length];
        b.position(start);
        b.get(encodedBody);
    }

    public boolean isEncoded() {
        return encodedBody != null;
    }

    /**
     * Just a place holder to make the APIs simpler.
     */
    public Packet encode(ByteBuffer buffer, int channel) throws BufferTooSmallException {
        throw new UnsupportedOperationException("This class does not support a channel ID");
    }

    /**
     * Allocate the needed buffers and encode the packet using the given
     * channel ID (if pertinent).
     *
     * If this packet does not use a channel ID, the ID will be ignored.
     */
    public final void allocateAndEncode(int channelId) {
        allocateAndEncode(channelId, DEFAULT_WRITE_BUFFER_SIZE);
    }

    private void allocateAndEncode(int channelId, int initialSize) {
        if (encodingBuffer != null) {
            patchChannelId(encodingBuffer, channelId);
            return;
        }

        int size = initialSize;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        while (true) {
            try {
                if (hasChannelId()) {
                    encode(buffer, channelId);
                } else {
                    encode(buffer);
                }
                buffer.flip();
                encodingBuffer = buffer;
                break;
            }
            catch (BufferTooSmallException e) {
                size *= 2;
                buffer = ByteBuffer.allocate(size);
            }
        }
    }

    // No channel ID for BasicPacket instances, so it's a NOP
    protected void patchChannelId(ByteBuffer buf, int channelId) {}

    /**
     * Return buffer containing the encoded form of this package and
     * remove internal reference to it.
     */
    public final ByteBuffer grantEncodingBuffer(int channelId) {
        if (encodingBuffer == null) {
            allocateAndEncode(channelId);
        } else {
            patchChannelId(encodingBuffer, channelId);
        }
        ByteBuffer b = encodingBuffer;
        encodingBuffer = null;
        return b;
    }

    public final ByteBuffer grantEncodingBuffer(int channelId, int initialSize) {
        if (encodingBuffer == null) {
            allocateAndEncode(channelId, initialSize);
        } else {
            patchChannelId(encodingBuffer, channelId);
        }
        ByteBuffer b = encodingBuffer;
        encodingBuffer = null;
        return b;
    }

    /** Returns the code of this package */
    public abstract int getCode();

    /**
     * Returns the length of this body (including header (8 bytes) and body),
     * or -1 if not known.
     * Note that the streamed packet format length is 4 bytes less than this length,
     * for unknown reasons.
     * The length is always known when decodeBody is called.
     */
    public int getLength() {
        return length;
    }

    /**
     * Set the timestamp field of the packet.
     *
     * A timestamp which can be set or inspected by clients of this class
     * but which is never updated by the class itself.  This is mostly
     * a convenience for when you need to queue packets or retain them
     * in some structure where their validity is limited by a timeout
     * or similar.
     */
    public void setTimestamp (long timeStamp) {
        this.timeStamp = timeStamp;
    }

    /**
     * Get the timestamp field of this packet.  Note that this is
     * <b>not</b> part of the FS4 protocol.  @see #setTimestamp for
     * more information
     *
     */
    public long getTimestamp () {
        return timeStamp;
    }

    public String toString() {
        return "packet with code " + getCode();
    }

    /** Whether this is a packets which can encode a channel ID. */
    public boolean hasChannelId() {
        return false;
    }

}
