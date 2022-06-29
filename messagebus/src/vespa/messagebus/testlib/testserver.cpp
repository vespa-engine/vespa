// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "testserver.h"
#include "simpleprotocol.h"
#include "slobrok.h"
#include "slobrokstate.h"
#include <vespa/vespalib/component/vtag.h>
#include <thread>

namespace mbus {

VersionedRPCNetwork::VersionedRPCNetwork(const RPCNetworkParams &params) :
    RPCNetwork(params),
    _version(vespalib::Vtag::currentVersion)
{}

void
VersionedRPCNetwork::setVersion(const vespalib::Version &version)
{
    _version = version;
    flushTargetPool();
}

TestServer::TestServer(const Identity &ident,
                       const RoutingSpec &spec,
                       const Slobrok &slobrok,
                       IProtocol::SP protocol) :
    net(RPCNetworkParams(slobrok.config()).setIdentity(ident)),
    mb(net, ProtocolSet().add(std::make_shared<SimpleProtocol>()).add(protocol))
{
    mb.setupRouting(spec);
}

TestServer::TestServer(const MessageBusParams &mbusParams,
                       const RPCNetworkParams &netParams) :
    net(netParams),
    mb(net, mbusParams)
{}

TestServer::~TestServer() = default;

bool
TestServer::waitSlobrok(const string &pattern, uint32_t cnt)
{
    return waitState(SlobrokState().add(pattern, cnt));
}

bool
TestServer::waitState(const SlobrokState &slobrokState)
{
    for (uint32_t i = 0; i < 12000; ++i) {
        bool done = true;
        for (SlobrokState::ITR itr = slobrokState.begin();
             itr != slobrokState.end(); ++itr)
        {
            slobrok::api::IMirrorAPI::SpecList res = net.getMirror().lookup(itr->first);
            if (res.size() != itr->second) {
                done = false;
            }
        }
        if (done) {
            return true;
        }
        std::this_thread::sleep_for(10ms);
    }
    return false;
}

}
