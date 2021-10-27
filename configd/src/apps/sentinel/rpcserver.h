// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

#include "cmdq.h"
#include "model-owner.h"
#include "rpchooks.h"
#include <vespa/fnet/frt/supervisor.h>

namespace config::sentinel {

class RpcServer
{
private:
    fnet::frt::StandaloneFRT _server;
    RPCHooks _rpcHooks;
    int _port;

public:
    RpcServer(int port, CommandQueue &cmdQ, ModelOwner &modelOwner);
    ~RpcServer();

    int getPort() const { return _port; }
    FRT_Supervisor &orb() { return _server.supervisor(); }
};

} // namespace config::sentinel
