// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_stats.h"

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
public:
    enum class SubDb { NONE, READY, NOTREADY };

private:
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    AttributeUsageStats                       _usage;
    Mutex                                     _lock;
    std::vector<initializer::LoadMemoryUsage> _ready_load_memory_usages;
    std::vector<initializer::LoadMemoryUsage> _notready_load_memory_usages;
    AttributeUsageFilter&                     _filter;

public:
    AttributeUsageSamplerContext(const std::string& document_type, uint32_t ready_attributes,
                                 uint32_t notready_attributes, AttributeUsageFilter& filter);
    ~AttributeUsageSamplerContext();
    void merge(const search::AddressSpaceUsage& usage, const initializer::LoadMemoryUsage& load_memory_usage,
               SubDb sub_db, const std::string& attributeName, const std::string& subDbName);
    const AttributeUsageStats& getUsage() const { return _usage; }
};

} // namespace proton
