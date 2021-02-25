// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_stats.h"
#include "attribute_usage_filter_config.h"
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <mutex>
#include <atomic>
#include <memory>

namespace proton {

class IAttributeUsageListener;

/**
 * Class to filter write operations based on sampled information about
 * attribute resource usage (e.g. enum store and multivalue mapping).
 * If resource limit is reached then further writes are denied in
 * order to prevent entering an unrecoverable state.
 */
class AttributeUsageFilter : public IResourceWriteFilter {
public:
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    using Config = AttributeUsageFilterConfig;

private:
    mutable Mutex       _lock; // protect _attributeStats, _config, _state
    AttributeUsageStats _attributeStats;
    Config              _config;
    State               _state;
    std::atomic<bool>   _acceptWrite;
    std::unique_ptr<IAttributeUsageListener> _listener;

    void recalcState(const Guard &guard); // called with _lock held
public:
    AttributeUsageFilter();
    ~AttributeUsageFilter() override;
    void setAttributeStats(AttributeUsageStats attributeStats_in);
    AttributeUsageStats getAttributeUsageStats() const;
    void setConfig(Config config);
    void set_listener(std::unique_ptr<IAttributeUsageListener> listener);
    double getEnumStoreUsedRatio() const;
    double getMultiValueUsedRatio() const;
    bool acceptWriteOperation() const override;
    State getAcceptState() const override;
};


} // namespace proton
