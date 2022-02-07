// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "messagebus.h"
#include "configagent.h"
#include "protocolset.h"
#include <vespa/messagebus/network/rpcnetwork.h>

namespace config {class ConfigUri; }

namespace mbus {

/**
 * The RPCMessageBus class wraps a MessageBus with an RPCNetwork and handles
 * reconfiguration. The RPCMessageBus constructor will perform setup, while the
 * RPCMessageBus destructor will perform controlled shutdown and cleanup. Please
 * note that according to the object delete order, you must delete all sessions
 * before deleting the underlying MessageBus object.
 */
class RPCMessageBus {
private:
    RPCNetwork  _net;
    MessageBus  _bus;
    ConfigAgent _agent;
    config::ConfigFetcher _subscriber;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<RPCMessageBus> UP;
    typedef std::shared_ptr<RPCMessageBus> SP;
    RPCMessageBus(const RPCMessageBus &) = delete;
    RPCMessageBus & operator = (const RPCMessageBus &) = delete;

    /**
     * Constructs a new instance of this class.
     *
     * @param mbusParams   A complete set of message bus parameters.
     * @param rpcParams    A complete set of network parameters.
     * @param routingCfgId The config id for message bus routing specs.
     */
    RPCMessageBus(const MessageBusParams &mbusParams,
                  const RPCNetworkParams &rpcParams,
                  const config::ConfigUri & routingCfgId);
    RPCMessageBus(const MessageBusParams &mbusParams,
                  const RPCNetworkParams &rpcParams);


    /**
     * This constructor requires an array of protocols that it is to support, as
     * well as the host application's config identifier. That identifier is
     * necessary so that all created sessions can be uniquely identified on the
     * network.
     *
     * @param protocols    An array of known protocols.
     * @param rpcParams    A complete set of network parameters.
     * @param routingCfgId The config id for messagebus routing specs.
     */
    RPCMessageBus(const ProtocolSet &protocols,
                  const RPCNetworkParams &rpcParams,
                  const config::ConfigUri & routingCfgId);

    /**
     * Destruct. This will destruct the internal MessageBus and RPCNetwork
     * objects, thus performing cleanup. Note that all sessions created from the
     * internal MessageBus object should be destructed before deleting this
     * object.
     **/
    ~RPCMessageBus();

    /**
     * Returns a reference to the contained message bus object.
     *
     * @return The mbus object.
     */
    MessageBus &getMessageBus() { return _bus; }

    /**
     * Returns a const reference to the contained message bus object.
     *
     * @return The mbus object.
     */
    const MessageBus &getMessageBus() const { return _bus; }

    /**
     * Returns a reference to the contained rpc network object.
     *
     * @return The rpc network.
     */
    RPCNetwork &getRPCNetwork() { return _net; }

    /**
     * Returns a const reference to the contained rpc network object.
     *
     * @return The rpc network.
     */
    const RPCNetwork &getRPCNetwork() const { return _net; }
};

} // namespace mbus

