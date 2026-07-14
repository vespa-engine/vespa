// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_stats_and_load_info.h"

#include <vespa/searchcore/proton/initializer/load_memory_usage.h>

#include <mutex>
#include <vector>

namespace proton {

class AttributeUsageFilter;
class AttributeConfigInspector;
class TransientResourceUsageProvider;

/*
 * Context for sampling attribute usage stats.
 * When instance is destroyed, the aggregated stats is passed on to attribute usage filter.
 */
class AttributeUsageSamplerContext {
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    AttributeUsageStatsAndLoadInfo _usage_stats_and_load_info;
    Mutex                          _lock;
    AttributeUsageFilter&          _filter;

public:
    AttributeUsageSamplerContext(const std::string& document_type, uint32_t ready_attributes,
                                 uint32_t notready_attributes, AttributeUsageFilter& filter);
    ~AttributeUsageSamplerContext();
    void merge(const search::AddressSpaceUsage& usage, const initializer::LoadMemoryUsage& load_memory_usage,
               AttributeUsageStatsAndLoadInfo::SubDb sub_db, const std::string& attributeName,
               const std::string& subDbName);
};

} // namespace proton
