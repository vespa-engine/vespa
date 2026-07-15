// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/attribute/attribute_usage_stats_and_load_info.h>
#include <vespa/vespalib/stllike/hash_map.h>

#include <memory>
#include <mutex>

namespace proton {

class IAttributeUsageAndLoadInfoListener;
class IAttributeUsageListener;

/*
 * Class used to aggregate attribute address space usage across multiple document types and forward
 * the highest usage.
 */
class AttributeUsageNotifier : public std::enable_shared_from_this<AttributeUsageNotifier> {
    class AttributeUsageListener;
    std::mutex                                                      _lock;
    vespalib::hash_map<std::string, AttributeUsageStatsAndLoadInfo> _attribute_usage;
    AttributeUsageStats                                             _max_attribute_usage;
    std::shared_ptr<IAttributeUsageListener>                        _resource_usage_notifier;
    bool                                                            _closed;

    bool scan_attribute_usage(std::lock_guard<std::mutex>&);
    void notify_attribute_usage(AttributeUsageStatsAndLoadInfo attribute_usage_and_load_info) noexcept;
    void notify_attribute_usage(); // Called with _lock held
public:
    AttributeUsageNotifier(std::shared_ptr<IAttributeUsageListener> resource_usage_notifier);
    ~AttributeUsageNotifier();
    void remove_document_type(const std::string& document_type) noexcept;
    std::unique_ptr<IAttributeUsageAndLoadInfoListener>
    make_attribute_usage_listener(const std::string& document_type);
    void close();
};

} // namespace proton
