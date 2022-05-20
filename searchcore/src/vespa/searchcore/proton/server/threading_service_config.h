// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/config/config-proton.h>
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
    uint32_t           _master_task_limit;
    uint32_t           _defaultTaskLimit;
    bool               _is_task_limit_hard;
    OptimizeFor        _optimize;
    uint32_t           _kindOfWatermark;
    vespalib::duration _reactionTime;         // Maximum reaction time to new tasks

private:
    ThreadingServiceConfig(uint32_t master_task_limit_, int32_t defaultTaskLimit_,
                           OptimizeFor optimize_, uint32_t kindOfWatermark_, vespalib::duration reactionTime_);

public:
    static ThreadingServiceConfig make(const ProtonConfig& cfg);
    static ThreadingServiceConfig make();
    void update(const ThreadingServiceConfig& cfg);
    uint32_t master_task_limit() const { return _master_task_limit; }
    uint32_t defaultTaskLimit() const { return _defaultTaskLimit; }
    bool is_task_limit_hard() const { return _is_task_limit_hard; }
    OptimizeFor optimize() const { return _optimize; }
    uint32_t kindOfwatermark() const { return _kindOfWatermark; }
    vespalib::duration reactionTime() const { return _reactionTime; }
    bool operator==(const ThreadingServiceConfig &rhs) const;
};

}
