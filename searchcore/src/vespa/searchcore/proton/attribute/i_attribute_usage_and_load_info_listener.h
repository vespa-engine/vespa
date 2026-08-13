// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_stats_and_load_info.h"

namespace proton {

/*
 * Interface class for listening to attribute usage and load info changes.
 */
class IAttributeUsageAndLoadInfoListener {
public:
    virtual ~IAttributeUsageAndLoadInfoListener() = default;
    virtual void notify_attribute_usage(AttributeUsageStatsAndLoadInfo attribute_usage_and_load_info) noexcept = 0;
};

} // namespace proton
