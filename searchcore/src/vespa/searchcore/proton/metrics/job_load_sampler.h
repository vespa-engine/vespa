// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    std::chrono::time_point<std::chrono::steady_clock> _lastSampleTime;
    std::chrono::time_point<std::chrono::steady_clock> _lastUpdateTime;
    uint32_t _currJobCnt;
    double _loadIntegral;

    void updateIntegral(std::chrono::time_point<std::chrono::steady_clock> now, uint32_t jobCnt);

public:
    /**
     * Start the sampler with now (in seconds).
     */
    JobLoadSampler(std::chrono::time_point<std::chrono::steady_clock> now);

    /**
     * Signal that a job starts now (in seconds).
     */
    void startJob(std::chrono::time_point<std::chrono::steady_clock> now);

    /**
     * Signal that a job ends now (in seconds).
     */
    void endJob(std::chrono::time_point<std::chrono::steady_clock> now);

    /**
     * Samples the average load from previous sample time to now (in seconds).
     */
    double sampleLoad(std::chrono::time_point<std::chrono::steady_clock> now);
};

} // namespace proton

