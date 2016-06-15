// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.job_tracker");
#include "job_tracker.h"
#include <vespa/fastos/timestamp.h>

using fastos::TimeStamp;
using fastos::ClockSystem;

namespace proton {

JobTracker::JobTracker(double now, vespalib::Lock &lock)
    : _sampler(now),
      _lock(lock)
{
}

double
JobTracker::sampleLoad(double now, const vespalib::LockGuard &guard)
{
    (void) guard;
    return _sampler.sampleLoad(now);
}

void
JobTracker::start()
{
    vespalib::LockGuard guard(_lock);
    _sampler.startJob(TimeStamp(ClockSystem::now()).sec());
}

void
JobTracker::end()
{
    vespalib::LockGuard guard(_lock);
    _sampler.endJob(TimeStamp(ClockSystem::now()).sec());
}

} // namespace proton
