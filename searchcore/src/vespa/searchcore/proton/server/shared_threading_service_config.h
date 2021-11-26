// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/common/hw_info.h>

namespace vespa::config::search::core::internal { class InternalProtonType; }

namespace proton {

/**
 * Config for the thread executors that are shared across all document dbs.
 */
class SharedThreadingServiceConfig {
public:
    using ProtonConfig = const vespa::config::search::core::internal::InternalProtonType;

private:
    uint32_t _shared_threads;
    uint32_t _shared_task_limit;
    uint32_t _warmup_threads;

public:
    SharedThreadingServiceConfig(uint32_t shared_threads_in,
                                 uint32_t shared_task_limit_in,
                                 uint32_t warmup_threads_in);

    static SharedThreadingServiceConfig make(const ProtonConfig& cfg, const HwInfo::Cpu& cpu_info);

    uint32_t shared_threads() const { return _shared_threads; }
    uint32_t shared_task_limit() const { return _shared_task_limit; }
    uint32_t warmup_threads() const { return _warmup_threads; }

};

}

