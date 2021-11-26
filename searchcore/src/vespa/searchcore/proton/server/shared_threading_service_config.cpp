// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_threading_service_config.h"
#include <vespa/searchcore/config/config-proton.h>
#include <cmath>

namespace proton {

using ProtonConfig = SharedThreadingServiceConfig::ProtonConfig;

SharedThreadingServiceConfig::SharedThreadingServiceConfig(uint32_t shared_threads_in,
                                                           uint32_t shared_task_limit_in,
                                                           uint32_t warmup_threads_in)
    : _shared_threads(shared_threads_in),
      _shared_task_limit(shared_task_limit_in),
      _warmup_threads(warmup_threads_in)
{
}

namespace {

size_t
derive_shared_threads(const ProtonConfig& cfg, const HwInfo::Cpu& cpu_info)
{
    size_t scaled_cores = (size_t)std::ceil(cpu_info.cores() * cfg.feeding.concurrency);

    // We need at least 1 guaranteed free worker in order to ensure progress.
    return std::max(scaled_cores, cfg.documentdb.size() + cfg.flush.maxconcurrent + 1);
}

}

SharedThreadingServiceConfig
SharedThreadingServiceConfig::make(const proton::SharedThreadingServiceConfig::ProtonConfig& cfg,
                                   const proton::HwInfo::Cpu& cpu_info)
{
    size_t shared_threads = derive_shared_threads(cfg, cpu_info);
    return proton::SharedThreadingServiceConfig(shared_threads, shared_threads * 16, 4);
}

}
