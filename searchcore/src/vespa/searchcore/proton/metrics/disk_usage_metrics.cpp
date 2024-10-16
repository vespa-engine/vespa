// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_usage_metrics.h"
#include <vespa/vespalib/util/memoryusage.h>

namespace proton {

DiskUsageMetrics::DiskUsageMetrics(metrics::MetricSet* parent)
    : MetricSet("disk_usage", {}, "The disk usage for a given component", parent),
      _size_on_disk("size_on_disk", {}, "Size on disk (bytes)", this)
{
}

DiskUsageMetrics::~DiskUsageMetrics() = default;

void
DiskUsageMetrics::update(uint64_t size_on_disk)
{
    _size_on_disk.set(size_on_disk);
}

}
