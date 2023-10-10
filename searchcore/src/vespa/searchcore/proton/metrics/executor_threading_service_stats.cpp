// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_threading_service_stats.h"

namespace proton {

ExecutorThreadingServiceStats::ExecutorThreadingServiceStats(Stats masterExecutorStats,
                                                             Stats indexExecutorStats,
                                                             Stats summaryExecutorStats)
    : _masterExecutorStats(masterExecutorStats),
      _indexExecutorStats(indexExecutorStats),
      _summaryExecutorStats(summaryExecutorStats)
{
}

ExecutorThreadingServiceStats::~ExecutorThreadingServiceStats() = default;


}
