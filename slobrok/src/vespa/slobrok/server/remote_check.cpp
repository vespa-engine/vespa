// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "remote_check.h"
#include "named_service.h"
#include "rpc_server_map.h"
#include "rpc_server_manager.h"
#include "remote_slobrok.h"
#include "random.h"
#include "exchange_manager.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.remote_check");

namespace slobrok {

RemoteCheck::RemoteCheck(FNET_Scheduler *sched,
                         RpcServerMap& rpcsrvmap,
                         RpcServerManager& rpcsrvman,
                         ExchangeManager& exch)
    : FNET_Task(sched),
      _rpcsrvmap(rpcsrvmap), _rpcsrvmanager(rpcsrvman), _exchanger(exch)
{
    double seconds = randomIn(15.3, 27.9);
    Schedule(seconds);
}


RemoteCheck::~RemoteCheck()
{
    Kill();
}


void
RemoteCheck::PerformTask()
{
    LOG(debug, "asking exchanger to health check");
    _exchanger.healthCheck();
    double seconds = randomIn(151.3, 179.7);
    Schedule(seconds);
}


} // namespace slobrok
