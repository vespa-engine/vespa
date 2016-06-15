// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>

#include <vespa/log/log.h>
LOG_SETUP(".remcheck");

#include <vespa/fnet/frt/frt.h>

#include "remote_check.h"
#include "ok_state.h"
#include "named_service.h"
#include "rpc_server_map.h"
#include "rpc_server_manager.h"
#include "remote_slobrok.h"
#include "sbenv.h"
#include "random.h"

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
