// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/network/rpcnetwork.h>

/**
 * Implements a dummy network on top of an rpc network, blocking anything from reaching the actual transmit
 * steps in the base class.
 */
class MyNetwork : public mbus::RPCNetwork {
private:
    std::vector<mbus::RoutingNode*> _nodes;

public:
    /**
     * Constructs a new network object.
     *
     * @param params The parameter object to pass to the rpc network.
     */
    MyNetwork(const mbus::RPCNetworkParams &params);

    // Overrides RPCNetwork.
    bool allocServiceAddress(mbus::RoutingNode &recipient) override;
    void freeServiceAddress(mbus::RoutingNode &recipient) override;
    void send(const mbus::Message &msg, const std::vector<mbus::RoutingNode*> &recipients) override;

    /**
     * Removes and returns the list of recipients that was most recently sent to.
     *
     * @param contexts The list to move the contexts to.
     */
    void removeNodes(std::vector<mbus::RoutingNode*> &nodes);
};
