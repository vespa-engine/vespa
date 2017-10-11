// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <cstdint>

namespace proton {

/**
 * Config for the threading service used by a documentdb.
 */
class ThreadingServiceConfig {
public:
    using ProtonConfig = vespa::config::search::core::ProtonConfig;

private:
    uint32_t _indexingThreads;
    uint32_t _defaultTaskLimit;
    uint32_t _semiUnboundTaskLimit;

private:
    ThreadingServiceConfig(uint32_t indexingThreads_,
                           uint32_t defaultTaskLimit_,
                           uint32_t semiUnboundTaskLimit_);

public:
    static ThreadingServiceConfig make(const ProtonConfig &cfg,
                                       const HwInfo::Cpu &cpuInfo);

    uint32_t indexingThreads() const { return _indexingThreads; }
    uint32_t defaultTaskLimit() const { return _defaultTaskLimit; }
    uint32_t semiUnboundTaskLimit() const { return _semiUnboundTaskLimit; }
};

}
