// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_threading_service_stats.h"

namespace proton {

ExecutorThreadingServiceStats::ExecutorThreadingServiceStats(Stats masterExecutorStats,
                                                             Stats indexExecutorStats,
                                                             Stats summaryExecutorStats,
                                                             Stats indexFieldInverterExecutorStats,
                                                             Stats indexFieldWriterExecutorStats,
                                                             Stats attributeFieldWriterExecutorStats)
    : _masterExecutorStats(masterExecutorStats),
      _indexExecutorStats(indexExecutorStats),
      _summaryExecutorStats(summaryExecutorStats),
      _indexFieldInverterExecutorStats(indexFieldInverterExecutorStats),
      _indexFieldWriterExecutorStats(indexFieldWriterExecutorStats),
      _attributeFieldWriterExecutorStats(attributeFieldWriterExecutorStats)
{
}

ExecutorThreadingServiceStats::~ExecutorThreadingServiceStats() = default;


}
