// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcserver.h"

#include <vespa/log/log.h>
LOG_SETUP(".rpcserver");

namespace config::sentinel {

RpcServer::RpcServer(int portNumber, CommandQueue &cmdQ)
    : _server(),
      _rpcHooks(cmdQ, _server.supervisor()),
      _port(portNumber)
{
    if (_server.supervisor().Listen(portNumber)) {
        LOG(config, "listening on port %d", portNumber);
    } else {
        LOG(error, "unable to listen to port %d", portNumber);
    }
}

RpcServer::~RpcServer() = default;

} // namespace config::sentinel
