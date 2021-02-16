// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/*
 * Struct representing config for when to filter write operations
 * due to attribute resource usage (e.g. enum store and multivalue mapping).
 * If resource limit is reached then further writes are denied in
 * order to prevent entering an unrecoverable state.
 *
 * The config is used by AttributeUsageFilter.
 */
struct AttributeUsageFilterConfig
{
    double _enumStoreLimit;
    double _multiValueLimit;

    AttributeUsageFilterConfig() noexcept
        : _enumStoreLimit(1.0),
          _multiValueLimit(1.0)
    { }

    AttributeUsageFilterConfig(double enumStoreLimit_in,
                               double multiValueLimit_in) noexcept
        : _enumStoreLimit(enumStoreLimit_in),
          _multiValueLimit(multiValueLimit_in)
    { }

    bool operator==(const AttributeUsageFilterConfig &rhs) const noexcept {
        return ((_enumStoreLimit == rhs._enumStoreLimit) &&
                (_multiValueLimit == rhs._multiValueLimit));
    }
};

} // namespace proton
