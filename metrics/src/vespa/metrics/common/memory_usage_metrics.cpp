// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_usage_metrics.h"
#include <vespa/vespalib/util/memoryusage.h>

namespace metrics {

MemoryUsageMetrics::MemoryUsageMetrics(metrics::MetricSet* parent)
    : MetricSet("memory_usage", {}, "The memory usage for a given component", parent),
      _allocated_bytes("allocated_bytes", {}, "The number of allocated bytes", this),
      _used_bytes("used_bytes", {}, "The number of used bytes (<= allocated_bytes)", this),
      _dead_bytes("dead_bytes", {}, "The number of dead bytes (<= used_bytes)", this),
      _on_hold_bytes("onhold_bytes", {}, "The number of bytes on hold", this)
{
}

MemoryUsageMetrics::~MemoryUsageMetrics() = default;

void
MemoryUsageMetrics::update(const vespalib::MemoryUsage& usage)
{
    _allocated_bytes.set(usage.allocatedBytes());
    _used_bytes.set(usage.usedBytes());
    _dead_bytes.set(usage.deadBytes());
    _on_hold_bytes.set(usage.allocatedBytesOnHold());
}

}
