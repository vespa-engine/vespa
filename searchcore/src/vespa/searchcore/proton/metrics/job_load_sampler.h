// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>

namespace proton {

/**
 * Class the samples job load average of jobs running in a given time interval.
 *
 * If 1 job runs during a complete interval the sampled load is 1.0,
 * if 2 jobs run for 0.7 intervals each the load is 1.4.
 */
class JobLoadSampler
{
private:
    using time_point = std::chrono::time_point<std::chrono::steady_clock>;
    time_point _lastSampleTime;
    time_point _lastUpdateTime;
    uint32_t _currJobCnt;
    double _loadIntegral;

    void updateIntegral(time_point now, uint32_t jobCnt);

public:
    /**
     * Start the sampler with now (in seconds).
     */
    JobLoadSampler(time_point now);

    /**
     * Signal that a job starts now (in seconds).
     */
    void startJob(time_point now);

    /**
     * Signal that a job ends now (in seconds).
     */
    void endJob(time_point now);

    /**
     * Samples the average load from previous sample time to now (in seconds).
     */
    double sampleLoad(time_point now);
};

} // namespace proton

