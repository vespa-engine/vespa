// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_usage_metrics.h"
#include <vespa/searchlib/util/memoryusage.h>

namespace proton {

MemoryUsageMetrics::MemoryUsageMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("memory_usage", {}, "The memory usage for a given component", parent),
      _allocatedBytes("allocated_bytes", {}, "The number of allocated bytes", this),
      _usedBytes("used_bytes", {}, "The number of used bytes (<= allocatedbytes)", this),
      _deadBytes("dead_bytes", {}, "The number of dead bytes (<= usedbytes)", this),
      _onHoldBytes("onhold_bytes", {}, "The number of bytes on hold", this)
{
}

MemoryUsageMetrics::~MemoryUsageMetrics() {}

void
MemoryUsageMetrics::update(const search::MemoryUsage &usage)
{
    _allocatedBytes.set(usage.allocatedBytes());
    _usedBytes.set(usage.usedBytes());
    _deadBytes.set(usage.deadBytes());
    _onHoldBytes.set(usage.allocatedBytesOnHold());
}

}
