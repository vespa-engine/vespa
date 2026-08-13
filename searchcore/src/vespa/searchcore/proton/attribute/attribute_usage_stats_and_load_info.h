// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_stats.h"

#include <vespa/searchcore/proton/initializer/load_memory_usage.h>

#include <vector>

namespace proton {

/**
 * Class representing attribute usage stats and load info for a document type.
 */
class AttributeUsageStatsAndLoadInfo {
    AttributeUsageStats                       _usage_stats;
    std::vector<initializer::LoadMemoryUsage> _ready_load_memory_usages;
    std::vector<initializer::LoadMemoryUsage> _notready_load_memory_usages;

    AttributeUsageStatsAndLoadInfo(const AttributeUsageStatsAndLoadInfo&);

public:
    enum class SubDb { NONE, READY, NOTREADY };

    AttributeUsageStatsAndLoadInfo();
    AttributeUsageStatsAndLoadInfo(const std::string& document_type, uint32_t ready_attributes,
                                   uint32_t notready_attributes);
    AttributeUsageStatsAndLoadInfo(AttributeUsageStatsAndLoadInfo&&) noexcept = default;
    ~AttributeUsageStatsAndLoadInfo();
    AttributeUsageStatsAndLoadInfo& operator=(const AttributeUsageStatsAndLoadInfo&) = delete;
    AttributeUsageStatsAndLoadInfo& operator=(AttributeUsageStatsAndLoadInfo&&) noexcept = default;
    void merge(const search::AddressSpaceUsage& usage, const initializer::LoadMemoryUsage& load_memory_usage,
               SubDb sub_db, const std::string& attributeName, const std::string& subDbName);
    [[nodiscard]] const AttributeUsageStats& usage_stats() const noexcept { return _usage_stats; }
    [[nodiscard]] std::vector<initializer::LoadMemoryUsage>& ready_load_memory_usages() noexcept {
        return _ready_load_memory_usages;
    }
    [[nodiscard]] std::vector<initializer::LoadMemoryUsage>& notready_load_memory_usages() noexcept {
        return _notready_load_memory_usages;
    }
    [[nodiscard]] AttributeUsageStatsAndLoadInfo clone() const;
};

} // namespace proton
