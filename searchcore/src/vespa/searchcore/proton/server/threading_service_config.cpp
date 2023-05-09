// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threading_service_config.h"
#include <vespa/config-proton.h>
#include <cmath>

namespace proton {

using ProtonConfig = ThreadingServiceConfig::ProtonConfig;
using OptimizeFor = vespalib::Executor::OptimizeFor;


ThreadingServiceConfig::ThreadingServiceConfig(uint32_t master_task_limit_,
                                               int32_t defaultTaskLimit_,
                                               OptimizeFor optimize_,
                                               uint32_t kindOfWatermark_,
                                               vespalib::duration reactionTime_)
    : _master_task_limit(master_task_limit_),
      _defaultTaskLimit(std::abs(defaultTaskLimit_)),
      _is_task_limit_hard(defaultTaskLimit_ >= 0),
      _optimize(optimize_),
      _kindOfWatermark(kindOfWatermark_),
      _reactionTime(reactionTime_)
{
}

namespace {

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
ThreadingServiceConfig::make(const ProtonConfig& cfg)
{
    return ThreadingServiceConfig(cfg.feeding.masterTaskLimit,
                                  cfg.indexing.tasklimit,
                                  selectOptimization(cfg.indexing.optimize),
                                  cfg.indexing.kindOfWatermark,
                                  vespalib::from_s(cfg.indexing.reactiontime));
}

ThreadingServiceConfig
ThreadingServiceConfig::make() {
    return ThreadingServiceConfig(0, 100, OptimizeFor::LATENCY, 0, 10ms);
}

void
ThreadingServiceConfig::update(const ThreadingServiceConfig& cfg)
{
    _master_task_limit = cfg._master_task_limit;
    _defaultTaskLimit = cfg._defaultTaskLimit;
}

bool
ThreadingServiceConfig::operator==(const ThreadingServiceConfig &rhs) const
{
    return _master_task_limit == rhs._master_task_limit &&
        _defaultTaskLimit == rhs._defaultTaskLimit &&
        _is_task_limit_hard == rhs._is_task_limit_hard &&
        _optimize == rhs._optimize &&
        _kindOfWatermark == rhs._kindOfWatermark &&
        _reactionTime == rhs._reactionTime;
}

}
