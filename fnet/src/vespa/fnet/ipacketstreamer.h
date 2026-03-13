// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "context.h"

class FNET_DataBuffer;
class FNET_Packet;

/**
 * Class used to do custom streaming of packets on network
 * connections. The application is responsible for implementing the
 * functionality of the packet streamer. It is recommended that it is
 * backed by a packet factory object.
 **/
class FNET_IPacketStreamer {
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FNET_IPacketStreamer() = default;

    /**
     * This method is called to obtain information about the next packet
     * located in the databuffer. The information obtained by calling
     * this method is used to resolve the application context of the
     * channel that should receive the packet and to ensure that the
     * entire packet is read into the databuffer before the @ref Decode
     * method is invoked. If this method returns true, the 'plen' output
     * value will contain the number of bytes required to be located in
     * the databuffer before the @ref Decode method is invoked. This
     * method is also the place for packet header syncing, as it is
     * allowed to discard data from the source databuffer. If this
     * method returns false, it should be called again after more data
     * is read into the source databuffer.
     *
     * If the contents of the source databuffer is not a valid packet
     * header, the 'broken' flag may be raised to indicate that the
     * connection should be closed due to illegal data being sent.
     *
     * @return true on success/false on fail
     * @param src databuffer to read packet information from
     * @param plen where to store packet length
     * @param pcode where to store the packet code
     * @param chid where to store the packet chid
     * @param broken where to signal broken data
     **/
    virtual bool GetPacketInfo(
        FNET_DataBuffer* src, uint32_t* plen, uint32_t* pcode, uint32_t* chid, bool* broken) = 0;

    /**
     * This method is called to un-stream a packet from the given
     * databuffer. This method will only be called after a call to the
     * @ref GetPacketInfo returns true and a number of bytes equal the
     * packet size indicated by that method is available in the
     * databuffer. The context of the channel that will receive the
     * packet is given as a parameter to this method in order to allow
     * application-layer customizations such as using memory pools. The
     * packet length and packet code output values from the @ref
     * GetPacketInfo method are given as parameters to this method to
     * avoid the need to parse the packet header twice.
     *
     * @return packet decoded from 'buf' or nullptr on failure
     * @param src buffer with the serialized packet
     * @param plen packet length as reported by @ref GetPacketInfo
     * @param pcode packet code as reported by @ref GetPacketInfo
     * @param context application context for target channel
     **/
    virtual FNET_Packet* Decode(FNET_DataBuffer* src, uint32_t plen, uint32_t pcode, FNET_Context context) = 0;

    /**
     * This method is called to stream a packet to the given databuffer.
     *
     * @param packet the packet to stream
     * @param chid channel id for packet
     * @param dst the target buffer for streaming
     **/
    virtual void Encode(FNET_Packet* packet, uint32_t chid, FNET_DataBuffer* dst) = 0;
};
