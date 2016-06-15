// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "job_load_sampler.h"
#include "i_job_tracker.h"
#include <vespa/vespalib/util/sync.h>

namespace proton {

/**
 * Class that tracks the start and end of jobs and makes average job load available.
 */
class JobTracker : public IJobTracker
{
private:
    JobLoadSampler  _sampler;
    vespalib::Lock &_lock;

public:
    typedef std::shared_ptr<JobTracker> SP;

    JobTracker(double now, vespalib::Lock &lock);

    /**
     * Samples the average job load from previous sample time to now (in seconds).
     * The caller of this function must take the guard on the lock referenced by this class.
     */
    double sampleLoad(double now, const vespalib::LockGuard &guard);

    // Implements IJobTracker
    virtual void start();
    virtual void end();
};

} // namespace proton

