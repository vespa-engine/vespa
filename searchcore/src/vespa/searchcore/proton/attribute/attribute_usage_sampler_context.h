// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_stats.h"
#include <mutex>
#include <memory>

namespace proton {

class AttributeUsageFilter;
class AttributeConfigInspector;
class TransientMemoryUsageProvider;

/*
 * Context for sampling attribute usage stats and transient memory usage.
 * When instance is destroyed, the aggregated stats is passed on to
 * attribute usage filter and the transient memory usage provider.
 */
class AttributeUsageSamplerContext
{
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    AttributeUsageStats _usage;
    size_t _transient_memory_usage;
    Mutex _lock;
    AttributeUsageFilter &_filter;
    std::shared_ptr<const AttributeConfigInspector> _attribute_config_inspector;
    std::shared_ptr<TransientMemoryUsageProvider> _transient_memory_usage_provider;
public:
    AttributeUsageSamplerContext(AttributeUsageFilter &filter, std::shared_ptr<const AttributeConfigInspector> attribute_config_inspector, std::shared_ptr<TransientMemoryUsageProvider> transient_memory_usage_provider);
    ~AttributeUsageSamplerContext();
    void merge(const search::AddressSpaceUsage &usage,
               size_t transient_memory_usage,
               const vespalib::string &attributeName,
               const vespalib::string &subDbName);
    const AttributeConfigInspector& get_attribute_config_inspector() const { return *_attribute_config_inspector; }
    const AttributeUsageStats &
    getUsage() const { return _usage; }
    size_t get_transient_memory_usage() const { return _transient_memory_usage; }
};

} // namespace proton
