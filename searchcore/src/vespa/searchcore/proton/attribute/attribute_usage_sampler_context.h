// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_stats.h"
#include <mutex>
#include <memory>

namespace proton {

class AttributeUsageFilter;
class AttributeConfigInspector;
class TransientResourceUsageProvider;

/*
 * Context for sampling attribute usage stats.
 * When instance is destroyed, the aggregated stats is passed on to attribute usage filter.
 */
class AttributeUsageSamplerContext
{
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    AttributeUsageStats _usage;
    Mutex _lock;
    AttributeUsageFilter &_filter;

public:
    AttributeUsageSamplerContext(AttributeUsageFilter& filter);
    ~AttributeUsageSamplerContext();
    void merge(const search::AddressSpaceUsage &usage,
               const vespalib::string &attributeName,
               const vespalib::string &subDbName);
    const AttributeUsageStats& getUsage() const { return _usage; }
};

} // namespace proton
