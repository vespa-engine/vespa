// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2005 Overture Services Norway AS

#include "perftask.h"
#include "appcontext.h"

#include <vespa/log/log.h>
LOG_SETUP(".perftask");

FastS_PerfTask::FastS_PerfTask(FastS_AppContext &ctx, double delay)
    : FNET_Task(ctx.GetFNETScheduler()),
      _ctx(ctx),
      _delay(delay),
      _valid(ctx.GetFNETScheduler() != NULL)
{
    if (_valid) {
        ScheduleNow();
    } else {
        LOG(warning, "Performance monitoring disabled; "
            "no scheduler found in application context");
    }
}


FastS_PerfTask::~FastS_PerfTask()
{
    if (_valid) {
        Kill();
    }
}


void
FastS_PerfTask::PerformTask()
{
    Schedule(_delay);
    _ctx.logPerformance();
}
