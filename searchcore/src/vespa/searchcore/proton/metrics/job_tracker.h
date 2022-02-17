// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "job_load_sampler.h"
#include "i_job_tracker.h"
#include <mutex>

namespace proton {

/**
 * Class that tracks the start and end of jobs and makes average job load available.
 */
class JobTracker : public IJobTracker
{
private:
    using time_point = std::chrono::time_point<std::chrono::steady_clock>;
    JobLoadSampler  _sampler;
    std::mutex     &_lock;

public:
    JobTracker(time_point now, std::mutex &lock);

    /**
     * Samples the average job load from previous sample time to now (in seconds).
     * The caller of this function must take the guard on the lock referenced by this class.
     */
    double sampleLoad(time_point now, const std::lock_guard<std::mutex> &guard);

    void start() override;
    void end() override;
};

}
