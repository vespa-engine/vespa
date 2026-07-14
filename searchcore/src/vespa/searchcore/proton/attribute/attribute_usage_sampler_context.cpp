// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_context.h"

#include "attribute_usage_filter.h"

namespace proton {

AttributeUsageSamplerContext::AttributeUsageSamplerContext(const std::string& document_type,
                                                           uint32_t ready_attributes, uint32_t notready_attributes,
                                                           AttributeUsageFilter& filter)
    : _usage(document_type), _lock(), _ready_load_memory_usages(), _notready_load_memory_usages(), _filter(filter) {
    _ready_load_memory_usages.reserve(ready_attributes);
    _notready_load_memory_usages.reserve(notready_attributes);
}

AttributeUsageSamplerContext::~AttributeUsageSamplerContext() {
    _filter.setAttributeStats(_usage);
}

void AttributeUsageSamplerContext::merge(const search::AddressSpaceUsage&    usage,
                                         const initializer::LoadMemoryUsage& load_memory_usage, SubDb sub_db,
                                         const std::string& attributeName, const std::string& subDbName) {
    Guard guard(_lock);
    _usage.merge(usage, attributeName, subDbName);
    switch (sub_db) {
    case SubDb::READY:
        _ready_load_memory_usages.emplace_back(load_memory_usage);
        break;
    case SubDb::NOTREADY:
        _notready_load_memory_usages.emplace_back(load_memory_usage);
        break;
    default:;
    }
}

} // namespace proton
