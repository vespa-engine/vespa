// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <memory>
#include <string>

class FNET_DataBuffer;

/**
 * This is a general superclass of all packets. Packets are used to
 * encapsulate data when communicating with other computers through
 * the network layer, or with the network layer itself. A packet may
 * be encoded into a byte stream representation held by a DataBuffer
 * object. The content of a DataBuffer may also be decoded into packet
 * member variables.
 **/
class FNET_Packet {
public:
    using UP = std::unique_ptr<FNET_Packet>;
    using SP = std::shared_ptr<FNET_Packet>;

    /** Does nothing. **/
    FNET_Packet() {}

    /** Does nothing. **/
    virtual ~FNET_Packet() = default;

    /**
     * This method is called to indicate that there is no more need for
     * this packet. In the FNET_Packet class this method is simply
     * implemented by deleting the packet object. Subclasses may
     * override this method to implement mechanisms like packet sharing
     * and/or pooling.
     **/
    virtual void Free() { delete this; }

    /**
     * Check if this is a regular packet. A regular packet may be
     * encoded into a DataBuffer and sent accross the network. Regular
     * packet implementations do not need to override this method.
     *
     * @return whether this is a regular packet (true)
     **/
    virtual bool IsRegularPacket() { return true; }

    /**
     * Check if this is a control packet. A control packet is a special
     * kind of packet used to report events in FNET. See the @ref
     * FNET_ControlPacket class for more information. Regular packet
     * implementations do not need to override this method.
     *
     * @return whether this is a control packet (false)
     **/
    virtual bool IsControlPacket() { return false; }

    /**
     * Method used to extract the command associated with this
     * packet. Packets that let the @ref IsControlPacket method return
     * false should always let this method return 0 (no command). See
     * the @ref FNET_ControlPacket class for more information. Regular
     * packet implementations do not need to override this method.
     *
     * @return packet command (0)
     **/
    virtual uint32_t GetCommand() { return 0; }

    /**
     * Convenience method used to check whether this packet is a control
     * packet signaling the loss of a channel. This method should return
     * true if and only if the @ref IsControlPacket method returns true
     * and the @ref GetCommand method returns
     * FNET_ControlPacket::FNET_CMD_CHANNEL_LOST. Regular packet
     * implementations do not need to override this method.
     *
     * @return is this a channel lost packet ? (false)
     **/
    virtual bool IsChannelLostCMD() { return false; }

    /**
     * Convenience method used to check whether this packet is a control
     * packet signaling a timeout. This method should return true if and
     * only if the @ref IsControlPacket method returns true and the @ref
     * GetCommand method returns
     * FNET_ControlPacket::FNET_CMD_TIMEOUT. Regular packet
     * implementations do not need to override this method. Note that
     * FNET does not use timeout packets internally. They are only
     * included to easy the implementation of timeout signaling in
     * applications using FNET.
     *
     * @return is this a timeout packet ? (false)
     **/
    virtual bool IsTimeoutCMD() { return false; }

    /**
     * Convenience method used to check whether this packet is a control
     * packet signaling a bad packet. This method should return true if
     * and only if the @ref IsControlPacket method returns true and the
     * @ref GetCommand method returns
     * FNET_ControlPacket::FNET_CMD_BAD_PACKET. Regular packet
     * implementations do not need to override this method. Whenever an
     * incoming packet may not be decoded from the network stream
     * (packet format protocol error), a bad packet control packet is
     * delivered instead.
     *
     * @return is this a badpacket packet ? (false)
     **/
    virtual bool IsBadPacketCMD() { return false; }

    /**
     * @return the packet code for this packet.
     **/
    virtual uint32_t GetPCODE() = 0;

    /**
     * @return encoded packet length in bytes
     **/
    virtual uint32_t GetLength() = 0;

    /**
     * Encode this packet into a DataBuffer. This method may only be
     * called on regular packets. See @ref IsRegularPacket.
     *
     * @param dst the target databuffer
     **/
    virtual void Encode(FNET_DataBuffer* dst) = 0;

    /**
     * Decode data from the given DataBuffer and store that information
     * in this object. This method may only be called on regular
     * packets. See @ref IsRegularPacket.
     *
     * @return true on success, false otherwise
     * @param src the data source
     * @param len length of the streamed representation
     **/
    virtual bool Decode(FNET_DataBuffer* src, uint32_t len) = 0;

    /**
     * Print a textual representation of this packet to stdout. This
     * method is used for debugging purposes.
     *
     * @param indent indent in number of spaces
     **/
    virtual std::string Print(uint32_t indent = 0);
};
