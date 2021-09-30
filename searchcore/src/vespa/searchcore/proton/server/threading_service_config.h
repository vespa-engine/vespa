// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/time.h>
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
    uint32_t           _indexingThreads;
    uint32_t           _defaultTaskLimit;
    OptimizeFor        _optimize;
    uint32_t           _kindOfWatermark;
    vespalib::duration _reactionTime;         // Maximum reaction time to new tasks

private:
    ThreadingServiceConfig(uint32_t indexingThreads_, uint32_t defaultTaskLimit_, OptimizeFor optimize, uint32_t kindOfWatermark, vespalib::duration reactionTime);

public:
    static ThreadingServiceConfig make(const ProtonConfig &cfg, double concurrency, const HwInfo::Cpu &cpuInfo);
    static ThreadingServiceConfig make(uint32_t indexingThreads);
    void update(const ThreadingServiceConfig& cfg);
    uint32_t indexingThreads() const { return _indexingThreads; }
    uint32_t defaultTaskLimit() const { return _defaultTaskLimit; }
    OptimizeFor optimize() const { return _optimize; }
    uint32_t kindOfwatermark() const { return _kindOfWatermark; }
    vespalib::duration reactionTime() const { return _reactionTime; }
    bool operator==(const ThreadingServiceConfig &rhs) const;
};

}
