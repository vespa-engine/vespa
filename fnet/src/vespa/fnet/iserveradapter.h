// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "channel.h"

/**
 * This class must be extended by the server application. It is needed
 * to let the application define the target packet handler for
 * incoming channels without creating a race condition.
 **/
class FNET_IServerAdapter {
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FNET_IServerAdapter() = default;

    /**
     * This method is called by the network layer when opening a new
     * channel on a connection handled by this server adapter. The
     * implementation of this method must define the target packet
     * handler and the application context for the given channel. The
     * 'pcode' parameter indicates the type of the first packet to be
     * received on this channel.
     *
     * @return success(true)/fail(false)
     * @param channel the channel being initialized.
     * @param pcode the packet type of the first packet on the channel.
     **/
    virtual bool InitChannel(FNET_Channel* channel, uint32_t pcode) = 0;
};
