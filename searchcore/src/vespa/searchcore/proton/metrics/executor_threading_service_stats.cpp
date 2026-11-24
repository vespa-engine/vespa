// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_threading_service_stats.h"

namespace proton {

ExecutorThreadingServiceStats::ExecutorThreadingServiceStats(Stats main_executor_stats_in,
                                                             Stats index_executor_stats_in,
                                                             Stats summary_executor_stats_in)
    : _main_executor_stats(main_executor_stats_in),
      _index_executor_stats(index_executor_stats_in),
      _summary_executor_stats(summary_executor_stats_in) {
}

ExecutorThreadingServiceStats::~ExecutorThreadingServiceStats() = default;

} // namespace proton
