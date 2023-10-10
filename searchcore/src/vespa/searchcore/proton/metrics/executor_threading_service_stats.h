// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>
#include <vespa/vespalib/util/executor_stats.h>

namespace proton {

/*
 * This class contains executor stats for the executors used by a
 * document db.
 */
class ExecutorThreadingServiceStats {
private:
    using Stats = vespalib::ExecutorStats;
    Stats _masterExecutorStats;
    Stats _indexExecutorStats;
    Stats _summaryExecutorStats;
public:
    ExecutorThreadingServiceStats(Stats masterExecutorStats,
                                  Stats indexExecutorStats,
                                  Stats summaryExecutorStats);
    ~ExecutorThreadingServiceStats();

    const Stats &getMasterExecutorStats() const { return _masterExecutorStats; }
    const Stats &getIndexExecutorStats() const { return _indexExecutorStats; }
    const Stats &getSummaryExecutorStats() const { return _summaryExecutorStats; }
};

}
