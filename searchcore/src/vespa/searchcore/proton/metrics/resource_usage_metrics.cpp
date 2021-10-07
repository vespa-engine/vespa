// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_metrics.h"

namespace proton {

ResourceUsageMetrics::ResourceUsageMetrics(metrics::MetricSet *parent)
    : MetricSet("resource_usage", {}, "Usage metrics for various resources in this search engine", parent),
      disk("disk", {}, "The relative amount of disk space used on this machine (value in the range [0, 1])", this),
      diskUtilization("disk_utilization", {}, "The relative amount of disk used compared to the disk resource limit", this),
      memory("memory", {}, "The relative amount of memory used by this process (value in the range [0, 1])", this),
      memoryUtilization("memory_utilization", {}, "The relative amount of memory used compared to the memory resource limit", this),
      transient_memory("transient_memory", {}, "The relative amount of transient memory needed for loading attributes. Max value among all attributes (value in the range [0, 1])", this),
      transient_disk("transient_disk", {}, "The relative amount of transient disk needed for running disk index fusion. Max value among all disk indexes (value in the range [0, 1])", this),
      memoryMappings("memory_mappings", {}, "The number of mapped memory areas", this),
      openFileDescriptors("open_file_descriptors", {}, "The number of open files", this),
      feedingBlocked("feeding_blocked", {}, "Whether feeding is blocked due to resource limits being reached (value is either 0 or 1)", this),
      mallocArena("malloc_arena", {}, "Size of malloc arena", this)
{
}

ResourceUsageMetrics::~ResourceUsageMetrics() = default;

} // namespace proton
