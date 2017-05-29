// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "selfcheck.h"
#include "ok_state.h"
#include "named_service.h"
#include "rpc_server_map.h"
#include "rpc_server_manager.h"
#include "managed_rpc_server.h"
#include "random.h"

#include <vespa/log/log.h>
LOG_SETUP(".selfcheck");

namespace slobrok {


SelfCheck::SelfCheck(FNET_Scheduler *sched,
                     RpcServerMap& rpcsrvmap,
                     RpcServerManager& rpcsrvman)
    : FNET_Task(sched),
      _rpcsrvmap(rpcsrvmap), _rpcsrvmanager(rpcsrvman)
{
    // start within 1 second
    double seconds = randomIn(0.123, 1.000);
    LOG(debug, "selfcheck in %g seconds", seconds);
    Schedule(seconds);
}


SelfCheck::~SelfCheck()
{
    Kill();
}


void
SelfCheck::PerformTask()
{
    std::vector<const NamedService *> mrpcsrvlist = _rpcsrvmap.allManaged();

    for (size_t i = 0; i < mrpcsrvlist.size(); ++i) {
        const NamedService *r = mrpcsrvlist[i];
        ManagedRpcServer *m = _rpcsrvmap.lookupManaged(r->getName());
        LOG_ASSERT(r == m);
        LOG(debug, "managed: %s -> %s", m->getName(), m->getSpec());
        m->healthCheck();
    }
    // reschedule in 1-2 seconds:
    double seconds = randomIn(0.987, 2.000);
    LOG(debug, "selfcheck AGAIN in %g seconds", seconds);
    Schedule(seconds);
}


} // namespace slobrok
