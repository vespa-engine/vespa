// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "resource_usage_metrics.h"

namespace proton {

ResourceUsageMetrics::ResourceUsageMetrics(metrics::MetricSet *parent)
    : MetricSet("resource_usage", "", "Usage metrics for various resources in this search engine", parent),
      disk("disk", "", "The relative amount of disk space used on this machine (value in the range [0, 1])", this),
      memory("memory", "", "The relative amount of memory used by this process (value in the range [0, 1])", this),
      memoryMappings("memory_mappings_count", "", "The number of mapped memory areas", this),
      feedingBlocked("feeding_blocked", "", "Whether feeding is blocked due to resource limits being reached (value is either 0 or 1)", this)
{
}

} // namespace proton
