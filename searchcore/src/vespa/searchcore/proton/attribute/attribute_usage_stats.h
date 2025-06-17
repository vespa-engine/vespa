// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "address_space_usage_stats.h"
#include <vespa/searchlib/attribute/address_space_usage.h>

namespace proton {

/**
 * Class representing aggregated max address space usage
 * among components in attributes vectors in all sub databases.
 */
class AttributeUsageStats
{
    AddressSpaceUsageStats _max_usage;
    std::string            _document_type;

public:
    AttributeUsageStats();
    AttributeUsageStats(const std::string& document_type_in);
    AttributeUsageStats(const AttributeUsageStats&);
    AttributeUsageStats(AttributeUsageStats&&) noexcept = default;
    ~AttributeUsageStats();
    AttributeUsageStats& operator=(const AttributeUsageStats&);
    AttributeUsageStats& operator=(AttributeUsageStats&&) noexcept = default;
    void merge(const search::AddressSpaceUsage &usage,
               const std::string &attributeName,
               const std::string &subDbName);

    const AddressSpaceUsageStats& max_address_space_usage() const noexcept { return _max_usage; }
    const std::string& document_type() const noexcept { return _document_type; }

    bool operator==(const AttributeUsageStats& rhs) const {
        return (_max_usage == rhs._max_usage) &&
               (_document_type == rhs._document_type);
    }

    bool less_usage_than(const AttributeUsageStats& new_stats) const noexcept {
        // Prefer the highest usage, then lowest document type
        auto old_usage = max_address_space_usage().getUsage().usage();
        auto new_usage = new_stats.max_address_space_usage().getUsage().usage();
        if (old_usage != new_usage) {
            return old_usage < new_usage;
        }
        return document_type() > new_stats.document_type();
    }
};

std::ostream& operator<<(std::ostream& out, const AttributeUsageStats& rhs);

} // namespace proton
