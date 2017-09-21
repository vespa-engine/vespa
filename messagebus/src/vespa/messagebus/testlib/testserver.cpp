// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "testserver.h"
#include "oosstate.h"
#include "simpleprotocol.h"
#include "slobrok.h"
#include "slobrokstate.h"
#include <vespa/messagebus/network/oosmanager.h>
#include <vespa/vespalib/component/vtag.h>

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
                       const string &oosServerPattern,
                       IProtocol::SP protocol) :
    net(RPCNetworkParams()
        .setIdentity(ident)
        .setSlobrokConfig(slobrok.config())
        .setOOSServerPattern(oosServerPattern)),
    mb(net, ProtocolSet().add(IProtocol::SP(new SimpleProtocol())).add(protocol))
{
    mb.setupRouting(spec);
}

TestServer::TestServer(const MessageBusParams &mbusParams,
                       const RPCNetworkParams &netParams) :
    net(netParams),
    mb(net, mbusParams)
{}

TestServer::~TestServer() {}

bool
TestServer::waitSlobrok(const string &pattern, uint32_t cnt)
{
    return waitState(SlobrokState().add(pattern, cnt));
}

bool
TestServer::waitOOS(const string &service)
{
    return waitState(OOSState().add(service, true));
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
        FastOS_Thread::Sleep(10);
    }
    return false;
}

bool
TestServer::waitState(const OOSState &oosState)
{
    for (uint32_t i = 0; i < 12000; ++i) {
        bool done = true;
        for (OOSState::ITR itr = oosState.begin();
             itr != oosState.end(); ++itr)
        {
            if (net.getOOSManager().isOOS(itr->first) != itr->second) {
                done = false;
            }
        }
        if (done) {
            return true;
        }
        FastOS_Thread::Sleep(10);
    }
    return false;
}

}
