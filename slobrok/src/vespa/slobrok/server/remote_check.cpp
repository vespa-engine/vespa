// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "remote_check.h"
#include "named_service.h"
#include "remote_slobrok.h"
#include "random.h"
#include "exchange_manager.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.remote_check");

namespace slobrok {

RemoteCheck::RemoteCheck(FNET_Scheduler *sched, ExchangeManager& exch)
    : FNET_Task(sched),
      _exchanger(exch)
{
    double seconds = randomIn(5.3, 9.7);
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
    double seconds = randomIn(15.3, 17.7);
    Schedule(seconds);
}


} // namespace slobrok
