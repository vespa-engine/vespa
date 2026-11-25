// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor_stats.h>

#include <cstddef>

namespace proton {

/*
 * This class contains executor stats for the executors used by a document db.
 */
class ExecutorThreadingServiceStats {
private:
    using Stats = vespalib::ExecutorStats;
    Stats _main_executor_stats;
    Stats _index_executor_stats;
    Stats _summary_executor_stats;

public:
    ExecutorThreadingServiceStats(Stats main_executor_stats_in, Stats index_executor_stats_in,
                                  Stats summary_executor_stats_in);
    ~ExecutorThreadingServiceStats();

    [[nodiscard]] const Stats& main_executor_stats() const noexcept { return _main_executor_stats; }
    [[nodiscard]] const Stats& index_executor_stats() const noexcept { return _index_executor_stats; }
    [[nodiscard]] const Stats& summary_executor_stats() const noexcept { return _summary_executor_stats; }
};

} // namespace proton
