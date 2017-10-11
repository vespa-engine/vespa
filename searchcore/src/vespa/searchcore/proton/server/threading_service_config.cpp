// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threading_service_config.h"
#include <vespa/searchcore/proton/common/hw_info.h>
#include <cmath>

namespace proton {

using ProtonConfig = ThreadingServiceConfig::ProtonConfig;

ThreadingServiceConfig::ThreadingServiceConfig(uint32_t indexingThreads_,
                                               uint32_t defaultTaskLimit_,
                                               uint32_t semiUnboundTaskLimit_)
    : _indexingThreads(indexingThreads_),
      _defaultTaskLimit(defaultTaskLimit_),
      _semiUnboundTaskLimit(semiUnboundTaskLimit_)
{
}

namespace {

uint32_t
calculateIndexingThreads(const ProtonConfig &cfg,
                         const HwInfo::Cpu &cpuInfo)
{
    double scaledCores = cpuInfo.cores() * cfg.feeding.concurrency;
    uint32_t indexingThreads = std::max((uint32_t)std::ceil(scaledCores / 3), (uint32_t)cfg.indexing.threads);
    return std::max(indexingThreads, 1u);
}

}

ThreadingServiceConfig
ThreadingServiceConfig::make(const ProtonConfig &cfg,
                             const HwInfo::Cpu &cpuInfo)
{
    uint32_t indexingThreads = calculateIndexingThreads(cfg, cpuInfo);
    return ThreadingServiceConfig(indexingThreads,
                                  cfg.indexing.tasklimit,
                                  (cfg.indexing.semiunboundtasklimit / indexingThreads));
}

}
