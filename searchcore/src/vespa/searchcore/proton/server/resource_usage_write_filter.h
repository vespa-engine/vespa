// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resource_usage_state.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_filter_config.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <atomic>
#include <mutex>

namespace proton {

/**
 * Class to filter write operations based on sampled disk and memory usage.
 * If resource limit is reached then further writes are denied
 * in order to prevent entering an unrecoverable state.
 */
class ResourceUsageWriteFilter : public IResourceWriteFilter {
public:
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

private:
    mutable Mutex                _lock;
    const vespalib::HwInfo       _hwInfo;
    std::atomic<bool>            _acceptWrite;
    // Following member variables are protected by _lock
    vespalib::ProcessMemoryStats _memoryStats;
    uint64_t                     _diskUsedSizeBytes;
    State                        _state;
    ResourceUsageState           _usage_state;
    AttributeUsageFilterConfig   _attribute_usage_filter_config;

    void recalc_state(const Guard& guard);
public:
    ResourceUsageWriteFilter(const vespalib::HwInfo& hwInfo);
    ~ResourceUsageWriteFilter() override;
    bool acceptWriteOperation() const override;
    State getAcceptState() const override;
    const vespalib::HwInfo& get_hw_info() const noexcept { return _hwInfo; }
    void notify_resource_usage(const ResourceUsageState& state, const vespalib::ProcessMemoryStats &memoryStats,
                               uint64_t diskUsedSizeBytes);
    void set_config(AttributeUsageFilterConfig attribute_usage_filter_config);
};

} // namespace proton
