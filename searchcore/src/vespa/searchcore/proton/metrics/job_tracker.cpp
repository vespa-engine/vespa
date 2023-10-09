// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "job_tracker.h"

namespace proton {

JobTracker::JobTracker(time_point now, std::mutex &lock)
    : _sampler(now),
      _lock(lock)
{
}

double
JobTracker::sampleLoad(time_point now, const std::lock_guard<std::mutex> &guard)
{
    (void) guard;
    return _sampler.sampleLoad(now);
}

void
JobTracker::start()
{
    std::lock_guard<std::mutex> guard(_lock);
    _sampler.startJob(std::chrono::steady_clock::now());
}

void
JobTracker::end()
{
    std::lock_guard<std::mutex> guard(_lock);
    _sampler.endJob(std::chrono::steady_clock::now());
}

} // namespace proton
