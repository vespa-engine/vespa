// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "context.h"

class FNET_Packet;

/**
 * Interface implemented by objects that can handle packets.
 **/
class FNET_IPacketHandler {
public:
    /**
     * This enum defines the possible values returned from the @ref
     * HandlePacket method. The @ref HandlePacket method is called on
     * the packet handler registered as the end-point of a channel when
     * a packet is received on that channel. The return value tells FNET
     * what to do with the channel; keep it open, close it or free
     * it. If the channel is closed, no more packets will be delivered
     * from FNET on that channel. The application however, may still use
     * a closed channel to send packets. If the channel is freed, it
     * will be closed in both directions and may not be used by the
     * application.
     **/
    enum HP_RetCode { FNET_KEEP_CHANNEL = 0, FNET_CLOSE_CHANNEL = 1, FNET_FREE_CHANNEL = 2 };

    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FNET_IPacketHandler() = default;

    /**
     * Handle an incoming packet in the given context. All incoming
     * packets are received through some channel. The application should
     * assign appropriate contexts to the different channels in order to
     * differentiate between them. Due to thread-restrictions the
     * channel on which a packet was received may not be closed during
     * the HandlePacket callback. However, the return code of this
     * method may tell FNET to keep the channel open, to close the
     * channel or to free the channel (freeing the channel implicitly
     * closes it first). NOTE: packet handover (caller TO invoked
     * object).
     *
     * @return channel command: keep open, close or free.
     * @param packet the packet to handle.
     * @param context the application context for the packet.
     **/
    virtual HP_RetCode HandlePacket(FNET_Packet* packet, FNET_Context context) = 0;
};
