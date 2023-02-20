// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/network/rpcnetwork.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/fnet/frt/supervisor.h>

namespace mbus {

class Identity;
class RoutingTableSpec;
class Slobrok;
class SlobrokState;

class VersionedRPCNetwork : public RPCNetwork {
private:
    vespalib::Version _version;

protected:
    const vespalib::Version &getVersion() const override { return _version; }

public:
    VersionedRPCNetwork(const RPCNetworkParams &params);
    ~VersionedRPCNetwork() override;
    void setVersion(const vespalib::Version &version);
};

class TestServer {
public:
    using UP = std::unique_ptr<TestServer>;
    TestServer(const TestServer &) = delete;
    TestServer & operator = (const TestServer &) = delete;

    VersionedRPCNetwork net;
    MessageBus mb;

    TestServer(const Identity &ident, const RoutingSpec &spec, const Slobrok &slobrok,
               IProtocol::SP protocol = IProtocol::SP());
    TestServer(const MessageBusParams &mbusParams, const RPCNetworkParams &netParams);
    ~TestServer();

    bool waitSlobrok(const string &pattern, uint32_t cnt = 1);
    bool waitState(const SlobrokState &slobrokState);
};

} // namespace mbus
