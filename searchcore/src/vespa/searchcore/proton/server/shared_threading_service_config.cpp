// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_threading_service_config.h"
#include <vespa/searchcore/config/config-proton.h>
#include <cmath>

namespace proton {

using ProtonConfig = SharedThreadingServiceConfig::ProtonConfig;

SharedThreadingServiceConfig::SharedThreadingServiceConfig(uint32_t shared_threads_in,
                                                           uint32_t shared_task_limit_in,
                                                           uint32_t warmup_threads_in,
                                                           uint32_t field_writer_threads_in,
                                                           double feeding_niceness_in,
                                                           const ThreadingServiceConfig& field_writer_config_in)
    : _shared_threads(shared_threads_in),
      _shared_task_limit(shared_task_limit_in),
      _warmup_threads(warmup_threads_in),
      _field_writer_threads(field_writer_threads_in),
      _feeding_niceness(feeding_niceness_in),
      _field_writer_config(field_writer_config_in)
{
}

namespace {

uint32_t
derive_shared_threads(const ProtonConfig& cfg, const HwInfo::Cpu& cpu_info)
{
    uint32_t scaled_cores = uint32_t(std::ceil(cpu_info.cores() * cfg.feeding.concurrency));

    // We need at least 1 guaranteed free worker in order to ensure progress.
    return std::max(scaled_cores, uint32_t(cfg.flush.maxconcurrent + 1u));
}

uint32_t
derive_warmup_threads(const HwInfo::Cpu& cpu_info) {
    return std::max(1u, std::min(4u, cpu_info.cores()/8));
}

uint32_t
derive_field_writer_threads(const ProtonConfig& cfg, const HwInfo::Cpu& cpu_info)
{
    uint32_t scaled_cores = size_t(std::ceil(cpu_info.cores() * cfg.feeding.concurrency));
    uint32_t field_writer_threads = std::max(scaled_cores, uint32_t(cfg.indexing.threads));
    // Originally we used at least 3 threads for writing fields:
    //   - index field inverter
    //   - index field writer
    //   - attribute field writer
    // We keep the same lower bound for similar behavior when using the shared field writer.
    return std::max(field_writer_threads, 3u);
}

}

SharedThreadingServiceConfig
SharedThreadingServiceConfig::make(const proton::SharedThreadingServiceConfig::ProtonConfig& cfg,
                                   const proton::HwInfo::Cpu& cpu_info)
{
    uint32_t shared_threads = derive_shared_threads(cfg, cpu_info);
    uint32_t field_writer_threads = derive_field_writer_threads(cfg, cpu_info);
    return proton::SharedThreadingServiceConfig(shared_threads, shared_threads * 16,
                                                derive_warmup_threads(cpu_info),
                                                field_writer_threads,
                                                cfg.feeding.niceness,
                                                ThreadingServiceConfig::make(cfg));
}

}
