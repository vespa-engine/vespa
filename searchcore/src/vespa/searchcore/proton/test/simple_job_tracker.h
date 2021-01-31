// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/metrics/i_job_tracker.h>
#include <vespa/vespalib/util/count_down_latch.h>

namespace proton::test {

struct SimpleJobTracker : public IJobTracker
{
    typedef std::shared_ptr<SimpleJobTracker> SP;
    vespalib::CountDownLatch _started;
    vespalib::CountDownLatch _ended;
    SimpleJobTracker(uint32_t numJobTrackings) noexcept
        : _started(numJobTrackings),
          _ended(numJobTrackings)
    {}

    // Implements IJobTracker
    void start() override { _started.countDown(); }
    void end() override { _ended.countDown(); }
};

}

