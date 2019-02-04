// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

#include "cmdq.h"
#include "rpchooks.h"
#include <vespa/fnet/frt/supervisor.h>

namespace config::sentinel {

class RpcServer
{
private:
    FRT_Supervisor _supervisor;
    RPCHooks _rpcHooks;
    int _port;

public:
    RpcServer(int port, CommandQueue &cmdQ);
    ~RpcServer();

    int getPort() const { return _port; }
};

} // namespace config::sentinel
