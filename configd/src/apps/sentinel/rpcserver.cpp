// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcserver.h"

#include <vespa/log/log.h>
LOG_SETUP(".rpcserver");

namespace config::sentinel {

RpcServer::RpcServer(int portNumber, CommandQueue &cmdQ)
    : _supervisor(),
      _rpcHooks(cmdQ),
      _port(portNumber)
{
    _rpcHooks.initRPC(&_supervisor);
    if (_supervisor.Listen(portNumber)) {
        LOG(config, "listening on port %d", portNumber);
        _supervisor.Start();
    } else {
        LOG(error, "unable to listen to port %d", portNumber);
    }
}

RpcServer::~RpcServer()
{
    _supervisor.ShutDown(true);
}

} // namespace config::sentinel
