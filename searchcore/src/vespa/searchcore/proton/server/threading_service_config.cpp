// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threading_service_config.h"
#include <vespa/searchcore/config/config-proton.h>
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
calculateIndexingThreads(uint32_t cfgIndexingThreads, double concurrency, const HwInfo::Cpu &cpuInfo)
{
    // We are capping at 12 threads to reduce cost of waking up threads
    // to achieve a better throughput.
    // TODO: Fix this in a simpler/better way.
    double scaledCores = std::min(12.0, cpuInfo.cores() * concurrency);
    uint32_t indexingThreads = std::max((uint32_t)std::ceil(scaledCores / 3), cfgIndexingThreads);
    return std::max(indexingThreads, 1u);
}

}

ThreadingServiceConfig
ThreadingServiceConfig::make(const ProtonConfig &cfg, double concurrency, const HwInfo::Cpu &cpuInfo)
{
    uint32_t indexingThreads = calculateIndexingThreads(cfg.indexing.threads, concurrency, cpuInfo);
    return ThreadingServiceConfig(indexingThreads, cfg.indexing.tasklimit,
                                  (cfg.indexing.semiunboundtasklimit / indexingThreads));
}

}
