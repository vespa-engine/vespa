// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threading_service_config.h"
#include <vespa/searchcore/config/config-proton.h>
#include <cmath>

namespace proton {

using ProtonConfig = ThreadingServiceConfig::ProtonConfig;
using OptimizeFor = vespalib::Executor::OptimizeFor;


ThreadingServiceConfig::ThreadingServiceConfig(uint32_t indexingThreads_,
                                               uint32_t defaultTaskLimit_,
                                               OptimizeFor optimize_,
                                               uint32_t kindOfWatermark_,
                                               vespalib::duration reactionTime_)
    : _indexingThreads(indexingThreads_),
      _defaultTaskLimit(defaultTaskLimit_),
      _optimize(optimize_),
      _kindOfWatermark(kindOfWatermark_),
      _reactionTime(reactionTime_)
{
}

namespace {

uint32_t
calculateIndexingThreads(const ProtonConfig::Indexing & indexing, double concurrency, const HwInfo::Cpu &cpuInfo)
{
    double scaledCores = cpuInfo.cores() * concurrency;
    if (indexing.optimize != ProtonConfig::Indexing::Optimize::ADAPTIVE) {
        // We are capping at 12 threads to reduce cost of waking up threads
        // to achieve a better throughput.
        // TODO: Fix this in a simpler/better way.
        scaledCores = std::min(12.0, scaledCores);
    }

    uint32_t indexingThreads = std::max((int32_t)std::ceil(scaledCores / 3), indexing.threads);
    return std::max(indexingThreads, 1u);
}

OptimizeFor
selectOptimization(ProtonConfig::Indexing::Optimize optimize) {
    using CfgOptimize = ProtonConfig::Indexing::Optimize;
    switch (optimize) {
        case CfgOptimize::LATENCY: return OptimizeFor::LATENCY;
        case CfgOptimize::THROUGHPUT: return OptimizeFor::THROUGHPUT;
        case CfgOptimize::ADAPTIVE: return OptimizeFor::ADAPTIVE;
    }
    return OptimizeFor::LATENCY;
}

}

ThreadingServiceConfig
ThreadingServiceConfig::make(const ProtonConfig &cfg, double concurrency, const HwInfo::Cpu &cpuInfo)
{
    uint32_t indexingThreads = calculateIndexingThreads(cfg.indexing, concurrency, cpuInfo);
    return ThreadingServiceConfig(indexingThreads, cfg.indexing.tasklimit,
                                  selectOptimization(cfg.indexing.optimize),
                                  cfg.indexing.kindOfWatermark,
                                  vespalib::from_s(cfg.indexing.reactiontime));
}

ThreadingServiceConfig
ThreadingServiceConfig::make(uint32_t indexingThreads) {
    return ThreadingServiceConfig(indexingThreads, 100, OptimizeFor::LATENCY, 0, 10ms);
}

void
ThreadingServiceConfig::update(const ThreadingServiceConfig& cfg)
{
    _defaultTaskLimit = cfg._defaultTaskLimit;
}

bool
ThreadingServiceConfig::operator==(const ThreadingServiceConfig &rhs) const
{
    return _indexingThreads == rhs._indexingThreads &&
        _defaultTaskLimit == rhs._defaultTaskLimit &&
        _optimize == rhs._optimize &&
        _kindOfWatermark == rhs._kindOfWatermark &&
        _reactionTime == rhs._reactionTime;
}

}
