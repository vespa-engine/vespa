// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "job_load_sampler.h"
#include <cassert>

namespace proton {

void
JobLoadSampler::updateIntegral(time_point now, uint32_t jobCnt)
{
    assert(now >= _lastUpdateTime);
    std::chrono::duration<double> duration = now - _lastUpdateTime;
    _loadIntegral += duration.count() * jobCnt;
    _lastUpdateTime = now;
}

JobLoadSampler::JobLoadSampler(time_point now)
    : _lastSampleTime(now),
      _lastUpdateTime(now),
      _currJobCnt(0),
      _loadIntegral(0)
{
}

void
JobLoadSampler::startJob(time_point now)
{
    updateIntegral(now, _currJobCnt++);
}

void
JobLoadSampler::endJob(time_point now)
{
    updateIntegral(now, _currJobCnt--);
}

double
JobLoadSampler::sampleLoad(time_point now)
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
