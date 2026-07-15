// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_context.h"

#include "attribute_usage_filter.h"

namespace proton {

AttributeUsageSamplerContext::AttributeUsageSamplerContext(const std::string& document_type,
                                                           uint32_t ready_attributes, uint32_t notready_attributes,
                                                           AttributeUsageFilter& filter)
    : _usage_stats_and_load_info(document_type, ready_attributes, notready_attributes), _lock(), _filter(filter) {
}

AttributeUsageSamplerContext::~AttributeUsageSamplerContext() {
    _filter.setAttributeStats(std::move(_usage_stats_and_load_info));
}

void AttributeUsageSamplerContext::merge(const search::AddressSpaceUsage&      usage,
                                         const initializer::LoadMemoryUsage&   load_memory_usage,
                                         AttributeUsageStatsAndLoadInfo::SubDb sub_db,
                                         const std::string& attributeName, const std::string& subDbName) {
    Guard guard(_lock);
    _usage_stats_and_load_info.merge(usage, load_memory_usage, sub_db, attributeName, subDbName);
}

} // namespace proton
