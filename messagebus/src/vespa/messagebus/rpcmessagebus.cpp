// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpcmessagebus.h"
#include <vespa/config/subscription/configuri.h>

namespace mbus {

RPCMessageBus::RPCMessageBus(const MessageBusParams &mbusParams,
                             const RPCNetworkParams &rpcParams,
                             const config::ConfigUri & routingCfgUri) :
    _net(rpcParams),
    _bus(_net, mbusParams),
    _agent(_bus),
    _subscriber(routingCfgUri.getContext())
{
    _subscriber.subscribe(routingCfgUri.getConfigId(), &_agent);
    _subscriber.start();
}

RPCMessageBus::RPCMessageBus(const MessageBusParams &mbusParams, const RPCNetworkParams &rpcParams)
    : RPCMessageBus(mbusParams, rpcParams, config::ConfigUri("client"))
{}

RPCMessageBus::RPCMessageBus(const ProtocolSet &protocols,
                             const RPCNetworkParams &rpcParams,
                             const config::ConfigUri &routingCfgUri) :
    _net(rpcParams),
    _bus(_net, protocols),
    _agent(_bus),
    _subscriber(routingCfgUri.getContext())
{
    _subscriber.subscribe(routingCfgUri.getConfigId(), &_agent);
    _subscriber.start();
}

RPCMessageBus::~RPCMessageBus()
{
    _subscriber.close();
}

} // namespace mbus
