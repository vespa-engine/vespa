// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.job_load_sampler");
#include "job_load_sampler.h"

namespace proton {

void
JobLoadSampler::updateIntegral(double now, uint32_t jobCnt)
{
    assert(now >= _lastUpdateTime);
    _loadIntegral += (now - _lastUpdateTime) * jobCnt;
    _lastUpdateTime = now;
}

JobLoadSampler::JobLoadSampler(double now)
    : _lastSampleTime(now),
      _lastUpdateTime(now),
      _currJobCnt(0),
      _loadIntegral(0)
{
}

void
JobLoadSampler::startJob(double now)
{
    updateIntegral(now, _currJobCnt++);
}

void
JobLoadSampler::endJob(double now)
{
    updateIntegral(now, _currJobCnt--);
}

double
JobLoadSampler::sampleLoad(double now)
{
    assert(now >= _lastSampleTime);
    updateIntegral(now, _currJobCnt);
    double load = (now - _lastSampleTime > 0) ? (_loadIntegral / (now - _lastSampleTime)) : 0;
    _lastSampleTime = now;
    _loadIntegral = 0;
    return load;
}

} // namespace proton
