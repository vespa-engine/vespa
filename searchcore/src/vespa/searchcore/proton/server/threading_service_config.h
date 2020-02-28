// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/vespalib/util/executor.h>
#include <cstdint>

namespace vespa::config::search::core::internal { class InternalProtonType; }
namespace proton {

/**
 * Config for the threading service used by a documentdb.
 */
class ThreadingServiceConfig {
public:
    using ProtonConfig = const vespa::config::search::core::internal::InternalProtonType;
    using OptimizeFor = vespalib::Executor::OptimizeFor;

private:
    uint32_t    _indexingThreads;
    uint32_t    _defaultTaskLimit;
    uint32_t    _semiUnboundTaskLimit;
    OptimizeFor _optimize;

private:
    ThreadingServiceConfig(uint32_t indexingThreads_, uint32_t defaultTaskLimit_, uint32_t semiUnboundTaskLimit_, OptimizeFor optimize);

public:
    static ThreadingServiceConfig make(const ProtonConfig &cfg, double concurrency, const HwInfo::Cpu &cpuInfo);

    uint32_t indexingThreads() const { return _indexingThreads; }
    uint32_t defaultTaskLimit() const { return _defaultTaskLimit; }
    uint32_t semiUnboundTaskLimit() const { return _semiUnboundTaskLimit; }
    OptimizeFor optimize() const { return _optimize;}
};

}
