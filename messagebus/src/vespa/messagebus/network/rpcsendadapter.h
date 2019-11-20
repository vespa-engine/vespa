// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/blobref.h>
#include <vespa/messagebus/common.h>
#include <vespa/vespalib/component/version.h>

namespace mbus {

class RoutingNode;
class RPCNetwork;

/**
 * This interface defines the necessary methods to process incoming and send
 * outgoing RPC sends. The {@link RPCNetwork} maintains a list of supported RPC
 * signatures, and dispatches sends to the corresponding adapter.
 */
class RPCSendAdapter
{
protected:
    RPCSendAdapter() = default;
public:
    RPCSendAdapter(const RPCSendAdapter &) = delete;
    RPCSendAdapter & operator = (const RPCSendAdapter &) = delete;
    /**
     * Required for inheritance.
     */
    virtual ~RPCSendAdapter() { }

    /**
     * Attaches this adapter to the given network.
     *
     * @param net The network to attach to.
     */
    virtual void attach(RPCNetwork &net) = 0;

    /**
     * Performs the actual sending to the given recipient.
     *
     * @param recipient     The recipient to send to.
     * @param version       The version for which the payload is serialized.
     * @param payload       The already serialized payload of the message to send.
     * @param timeRemaining The time remaining until the message expires.
     */
    virtual void send(RoutingNode &recipient, const vespalib::Version &version,
                      BlobRef payload, milliseconds timeRemaining) = 0;

    /**
     * Performs the actual sending to the given recipient.
     *
     * @param recipient     The recipient to send to.
     * @param version       The version for which the payload is serialized.
     * @param payload       The already serialized payload of the message to send.
     * @param timeRemaining The time remaining until the message expires.
     */
    virtual void sendByHandover(RoutingNode &recipient, const vespalib::Version &version,
                      Blob payload, milliseconds timeRemaining) = 0;
};

} // namespace mbus

