// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.job_load_sampler");
#include "job_load_sampler.h"

namespace proton {

void
JobLoadSampler::updateIntegral(std::chrono::time_point<std::chrono::steady_clock> now, uint32_t jobCnt)
{
    assert(now >= _lastUpdateTime);
    std::chrono::duration<double> duration = now - _lastUpdateTime;
    _loadIntegral += duration.count() * jobCnt;
    _lastUpdateTime = now;
}

JobLoadSampler::JobLoadSampler(std::chrono::time_point<std::chrono::steady_clock> now)
    : _lastSampleTime(now),
      _lastUpdateTime(now),
      _currJobCnt(0),
      _loadIntegral(0)
{
}

void
JobLoadSampler::startJob(std::chrono::time_point<std::chrono::steady_clock> now)
{
    updateIntegral(now, _currJobCnt++);
}

void
JobLoadSampler::endJob(std::chrono::time_point<std::chrono::steady_clock> now)
{
    updateIntegral(now, _currJobCnt--);
}

double
JobLoadSampler::sampleLoad(std::chrono::time_point<std::chrono::steady_clock> now)
{
    assert(now >= _lastSampleTime);
    updateIntegral(now, _currJobCnt);
    std::chrono::duration<double> duration = now - _lastSampleTime;
    double load = (duration.count() > 0) ? (_loadIntegral / duration.count()) : 0;
    _lastSampleTime = now;
    _loadIntegral = 0;
    return load;
}

} // namespace proton
