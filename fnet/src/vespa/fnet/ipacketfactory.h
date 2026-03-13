// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "context.h"

class FNET_Packet;

/**
 * Interface describing objects that are able to create packets. An
 * object implementing this interface is needed in order to use the
 * SimplePacketStreamer class.
 **/
class FNET_IPacketFactory {
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FNET_IPacketFactory() = default;

    /**
     * Create a packet.
     *
     * @return the newly created packet.
     * @param pcode what kind of packet to create. The semantic of this
     *        field is left to the implementing object to allow
     *        parallell dimensions of packet types in the same
     *        application.
     * @param context application context. When this class is used by
     *        the SimplePacketStreamer, this is the application context
     *        for the channel that will receive the packet after it is
     *        created and un-streamed.
     **/
    virtual FNET_Packet* CreatePacket(uint32_t pcode, FNET_Context context) = 0;
};
