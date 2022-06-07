// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_metrics.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace proton {

ResourceUsageMetrics::CpuUtilMetrics::CpuUtilMetrics(metrics::MetricSet *parent)
  : MetricSet("cpu_util", {}, "Unnormalized cpu utilization for various categories", parent),
    setup("setup", {}, "cpu used by system init and (re-)configuration", this),
    read("read", {}, "cpu used by reading data from the system", this),
    write("write", {}, "cpu used by writing data to the system", this),
    compact("compact", {}, "cpu used by internal data re-structuring", this),
    other("other", {}, "cpu used by work not classified as a specific category", this)
{
}

ResourceUsageMetrics::CpuUtilMetrics::~CpuUtilMetrics() = default;

ResourceUsageMetrics::DetailedResourceMetrics::DetailedResourceMetrics(const vespalib::string& resource_type, metrics::MetricSet* parent)
    : MetricSet(make_string("%s_usage", resource_type.c_str()), {}, make_string("Detailed resource usage metrics for %s",
                                                                                resource_type.c_str()), parent),
      total("total", {}, make_string("The total relative amount of %s used by this content node (value in the range [0, 1])",
                                     resource_type.c_str()), this),
      total_util("total_utilization", {}, make_string("The relative amount of %s used compared to the content node %s resource limit",
                                                      resource_type.c_str(), resource_type.c_str()), this),
      transient("transient", {}, make_string("The relative amount of transient %s used by this content node (value in the range [0, 1])",
                                             resource_type.c_str()), this)
{
}

ResourceUsageMetrics::DetailedResourceMetrics::~DetailedResourceMetrics() = default;

ResourceUsageMetrics::ResourceUsageMetrics(metrics::MetricSet *parent)
    : MetricSet("resource_usage", {}, "Usage metrics for various resources in this content node", parent),
      disk("disk", {}, "The relative amount of disk used by this content node (transient usage not included, value in the range [0, 1]). Same value as reported to the cluster controller", this),
      memory("memory", {}, "The relative amount of memory used by this content node (transient usage not included, value in the range [0, 1]). Same value as reported to the cluster controller", this),
      disk_usage("disk", this),
      memory_usage("memory", this),
      memoryMappings("memory_mappings", {}, "The number of mapped memory areas", this),
      openFileDescriptors("open_file_descriptors", {}, "The number of open files", this),
      feedingBlocked("feeding_blocked", {}, "Whether feeding is blocked due to resource limits being reached (value is either 0 or 1)", this),
      mallocArena("malloc_arena", {}, "Size of malloc arena", this),
      cpu_util(this)
{
}

ResourceUsageMetrics::~ResourceUsageMetrics() = default;

} // namespace proton
