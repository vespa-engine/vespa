// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * Superclass of fs4 packets containing channel/query ID
 *
 * @author bratseth
 */
public abstract class Packet extends BasicPacket {

    private static Logger log = Logger.getLogger(Packet.class.getName());

    /**
     * The channel at which this packet will be sent or was received,
     * or -1 when this is not known
     */
    protected int channel = -1;

    private static final int CHANNEL_ID_OFFSET = 8;

    /**
     * Fills this package from a byte buffer positioned at the first
     * byte of the package
     *
     * @return this Packet (as a BasicPacket) for convenience
     * @throws UnsupportedOperationException if not implemented in the subclass
     */
    public BasicPacket decode(ByteBuffer buffer) {
        int originalPos = buffer.position();
        length = buffer.getInt()+4; // Streamed packet length is the length-4
        int packetLength = length;
        try {
            int code = buffer.getInt();
            channel = buffer.getInt();

            decodeAndDecompressBody(buffer, code, length - 3*4);
        }
        finally {
            int targetPosition = (originalPos + packetLength);
            if (buffer.position() != targetPosition) {
                log.warning("Position in buffer is " + buffer.position() + " should be " + targetPosition);
                buffer.position(targetPosition);
            }
        }

        return this;
    }

    /**
     * <p>Encodes this package onto the given buffer at the current
     * position.  The position of the buffer after encoding is the
     * byte following the last encoded byte.</p>
     *
     * <p>This method will ensure that everything is written provided
     * sufficient capacity regardless of the buffer limit.
     * When returning, the limit is at the end of the package (qual to the
     * position).</p>
     *
     * @return this for convenience
     * @throws UnsupportedOperationException if not implemented in the subclass
     */
    public final Packet encode(ByteBuffer buffer, int channel) throws BufferTooSmallException {
        this.channel = channel;
        int oldLimit = buffer.limit();
        int startPosition = buffer.position();

        buffer.limit(buffer.capacity());
        try {
            buffer.putInt(8); // Real length written later, when we know it
            buffer.putInt(getCode());
            buffer.putInt(channel);

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

    /**
     * Get the channel id of the packet.  In the FS4 transport protocol,
     * there is the concept of a channel.  This must <b>not</b> be confused
     * with all the other channels we have floating around this code (aargh!).
     * <P>
     * The channel can be thought of as a way to pair up requests and
     * responses in the FS4 protocol:  A response always belongs to
     * to a channel and it is the clients responsibility to not re-use
     * channel ids within the same connection.
     * <p>
     * Summary: This "channel" means "session id"
     *
     * @return FS4 channel id
     *
     */
    public int getChannel() { return channel; }

    public void setChannel(int channel) { this.channel=channel; }


    /** Informs that this packets needs a channel ID. */
    public boolean hasChannelId() {
        return true;
    }

    /**
     * Only for use with encodingBuffer magic.
     *
     * This is only called from allocateAndEncode and grantEncodingBuffer,
     * therefore an assumption about the packet starting at the beginning of the
     * buffer is made.
     */
    protected void patchChannelId(ByteBuffer buf, int channelId) {
        buf.putInt(CHANNEL_ID_OFFSET, channelId);
    }

    public String toString() {
        return "packet with code " + getCode() + ", channelId=" + getChannel();
    }

}
