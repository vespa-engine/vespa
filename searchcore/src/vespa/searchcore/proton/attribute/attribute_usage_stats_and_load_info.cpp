// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_stats_and_load_info.h"

namespace proton {

AttributeUsageStatsAndLoadInfo::AttributeUsageStatsAndLoadInfo(const std::string& document_type,
                                                               uint32_t           ready_attributes,
                                                               uint32_t           notready_attributes)
    : _usage_stats(document_type), _ready_load_memory_usages(), _notready_load_memory_usages() {
    _ready_load_memory_usages.reserve(ready_attributes);
    _notready_load_memory_usages.reserve(notready_attributes);
}

AttributeUsageStatsAndLoadInfo::AttributeUsageStatsAndLoadInfo(const AttributeUsageStatsAndLoadInfo&) = default;

AttributeUsageStatsAndLoadInfo::~AttributeUsageStatsAndLoadInfo() = default;

AttributeUsageStatsAndLoadInfo&
AttributeUsageStatsAndLoadInfo::operator=(const AttributeUsageStatsAndLoadInfo&) = default;

void AttributeUsageStatsAndLoadInfo::merge(const search::AddressSpaceUsage&    usage,
                                           const initializer::LoadMemoryUsage& load_memory_usage, SubDb sub_db,
                                           const std::string& attributeName, const std::string& subDbName) {
    _usage_stats.merge(usage, attributeName, subDbName);
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
