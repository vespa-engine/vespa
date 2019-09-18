// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

/**
 * Returns the correct package for a package byte stream
 *
 * @author  bratseth
 * @author  <a href="mailto:borud@yahoo-inc.com">Bj\u00f8rn Borud</a>
 */
public class PacketDecoder {

    /** Represents a packet and the data used to construct it */
    public static class DecodedPacket {
        public BasicPacket packet;
        public ByteBuffer consumedBytes;

        DecodedPacket(BasicPacket packet, ByteBuffer consumedBytes) {
            this.packet = packet;
            this.consumedBytes = consumedBytes;
        }
    }

    private PacketDecoder() {}

    /**
     * Returns the package starting at the current position in the buffer
     *
     * @throws IllegalArgumentException if an unknown package code is
     *          encountered
     * @throws java.nio.BufferUnderflowException if the buffer contains too little
     *          data to decode the pcode.
     */
     public static BasicPacket decode(ByteBuffer buffer) {
         int packetCode = buffer.getInt(buffer.position()+4);
         packetCode &= BasicPacket.CODE_MASK;

         switch (packetCode) {
             case 200:
                 return EolPacket.create().decode(buffer);

             case 203:
                 return ErrorPacket.create().decode(buffer);

             case 205:
                 return DocsumPacket.create().decode(buffer);

             case 217:
                 return QueryResultPacket.create().decode(buffer);

             case 221:
                 return PongPacket.create().decode(buffer);

             default:
                 throw new IllegalArgumentException("No support for packet " + packetCode);
         }
    }

    /** Gives the packet along with the bytes consumed to construct it. */
    public static DecodedPacket decodePacket(ByteBuffer buffer) {
        ByteBuffer dataUsed = buffer.slice();
        int start = buffer.position();

        BasicPacket packet = decode(buffer);
        dataUsed.limit(buffer.position() - start);
        return new DecodedPacket(packet, dataUsed);
    }

     /** Sniff channel ID for query result packets */
     public static int sniffChannel(ByteBuffer buffer) {
        int remaining = buffer.remaining();
        if (remaining < 12) {
            return 0;
        }
         int packetCode = buffer.getInt(buffer.position()+4);
         packetCode &= BasicPacket.CODE_MASK;
         switch (packetCode) {
         case 202:
         case 208:
         case 214:
         case 217:
             return buffer.getInt(buffer.position()+8);
         default:
             return 0;
         }
     }

     /**
      * Test whether the buffer contains (the start of) a pong packet.
      *
      * Returns false if there is not enough data to determine the
      * answer.
      */
     public static boolean isPongPacket(ByteBuffer buffer) {

        int remaining = buffer.remaining();
        if (remaining < 8)
            return false;
         int packetCode = buffer.getInt(buffer.position()+4);
         packetCode &= BasicPacket.CODE_MASK;
         if (packetCode == 221)
             return true;
         else
             return false;
     }

    /**
     * Note that it assumes that the position of the ByteBuffer is at the
     * start of a packet and that we have enough data to actually read
     * an integer out of the buffer.
     *
     * @return Return the length of the fs4 packet.  Returns -1 if length
     *         could not be determined because we had too little
     *         data in the buffer.
     *
     */
    public static int packetLength(ByteBuffer buffer)
    {
        if (buffer.remaining() < 4) {
            return -1;
        }
        return (buffer.getInt(buffer.position()) + 4);
    }

    /**
     * Takes a buffer possibly containing a packet.
     *
     * <P>
     * If we return a packet when we return:
     * <UL>
     *  <LI> the buffer is positioned at the beginning of the next
     *       packet when we return.
     *  <LI> limit is unchanged
     * </UL>
     *
     * <P>
     * If we return <code>null</code> there were no more packets
     * there to decode and the following is true of the buffer
     * <UL>
     *  <LI> the buffer is compacted, ie. partial packet is
     *       moved to the start, or if no more data is available
     *       the buffer is cleared.
     *  <LI> the position is set to the next byte after the valid
     *       data so the buffer is ready for reading.
     * </UL>
     *
     * If there are no packets to be returned the buffer is compacted
     * (ie. content is moved to the start and read-pointer is positioned
     *
     * @return Returns the next available packet from the buffer or
     *         <code>null</code> if there are no more <b>complete</b>
     *         packets in the buffer at this time.
     */
    public static DecodedPacket extractPacket(ByteBuffer buffer)
        throws BufferTooSmallException
    {
        int remaining = buffer.remaining();

        // if we are empty we can reset the buffer
        if (remaining == 0) {
            buffer.clear();
            return null;
        }

        // if we can't figure out the size because we have less than
        // 4 bytes we prepare the buffer for more data reading.
        if (remaining < 4) {
            buffer.compact();
            return null;
        }

        int plen = packetLength(buffer);

        // -1 means that we do not have enough data to read the packet
        // size yet
        if (plen == -1) {
            buffer.compact();
            return null;
        }

        // if we haven't read an entire packet yet, we compact and return
        // (same as above but separate code for clarity).  note that this
        // also occurs when there is no physical room for the packet, so
        // clients of this API need to be aware of this and check for it
        if (remaining < plen) {

            // if the read buffer is too small we must take drastic action
            if (buffer.capacity() < plen) {
                throw new BufferTooSmallException("Buffer too small to hold packet");
            }

            buffer.compact();
            return null;
        }

        return PacketDecoder.decodePacket(buffer);
    }

}
