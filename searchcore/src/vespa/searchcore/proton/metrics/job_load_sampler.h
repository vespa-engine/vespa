// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    double _lastSampleTime;
    double _lastUpdateTime;
    uint32_t _currJobCnt;
    double _loadIntegral;

    void updateIntegral(double now, uint32_t jobCnt);

public:
    /**
     * Start the sampler with now (in seconds).
     */
    JobLoadSampler(double now);

    /**
     * Signal that a job starts now (in seconds).
     */
    void startJob(double now);

    /**
     * Signal that a job ends now (in seconds).
     */
    void endJob(double now);

    /**
     * Samples the average load from previous sample time to now (in seconds).
     */
    double sampleLoad(double now);
};

} // namespace proton

