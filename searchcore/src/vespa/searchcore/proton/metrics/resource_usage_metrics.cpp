// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_metrics.h"

namespace proton {

ResourceUsageMetrics::ResourceUsageMetrics(metrics::MetricSet *parent)
    : MetricSet("resource_usage", "", "Usage metrics for various resources in this search engine", parent),
      disk("disk", "", "The relative amount of disk space used on this machine (value in the range [0, 1])", this),
      memory("memory", "", "The relative amount of memory used by this process (value in the range [0, 1])", this),
      memoryMappings("memory_mappings", "", "The number of mapped memory areas", this),
      openFileDescriptors("open_file_descriptors", "", "The number of open files", this),
      feedingBlocked("feeding_blocked", "", "Whether feeding is blocked due to resource limits being reached (value is either 0 or 1)", this)
{
}

ResourceUsageMetrics::~ResourceUsageMetrics() {}

} // namespace proton
