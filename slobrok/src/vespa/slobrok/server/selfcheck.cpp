// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
      _rpcsrvmap(rpcsrvmap), _rpcsrvmanager(rpcsrvman),
      _checkIndex(0)
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
    if (_checkIndex < mrpcsrvlist.size()) {
        const NamedService *r = mrpcsrvlist[_checkIndex++];
        ManagedRpcServer *m = _rpcsrvmap.lookupManaged(r->getName());
        LOG(debug, "managed: %s -> %s", m->getName().c_str(), m->getSpec().c_str());
        LOG_ASSERT(r == m);
        m->healthCheck();
    } else {
        _checkIndex = 0;
    }
    // reschedule more often with more services, on average 1s per loop:
    double seconds = randomIn(0.5, 1.5) / (1 + mrpcsrvlist.size());
    LOG(debug, "next selfcheck in %g seconds", seconds);
    Schedule(seconds);
}

} // namespace slobrok
