// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Struct representing config for when to filter write operations
 * due to attribute resource usage (e.g. enum store and multivalue mapping).
 * If resource limit is reached then further writes are denied in
 * order to prevent entering an unrecoverable state.
 *
 * The config is used by AttributeUsageFilter.
 */
struct AttributeUsageFilterConfig
{
    double _address_space_limit;

    AttributeUsageFilterConfig() noexcept
        : _address_space_limit(1.0)
    {}

    AttributeUsageFilterConfig(double address_space_limit) noexcept
        : _address_space_limit(address_space_limit)
    {}

    bool operator==(const AttributeUsageFilterConfig &rhs) const noexcept {
        return (_address_space_limit == rhs._address_space_limit);
    }
};

} // namespace proton
