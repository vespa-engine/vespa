// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <memory>

namespace proton {

class IAttributeUsageListener;

/*
 * Class used to aggregate attribute address space usage across multiple document types and forward
 * the highest usage.
 */
class AttributeUsageNotifier : public std::enable_shared_from_this<AttributeUsageNotifier> {
    class AttributeUsageListener;
    std::mutex                                           _lock;
    vespalib::hash_map<std::string, AttributeUsageStats> _attribute_usage;
    AttributeUsageStats                                  _max_attribute_usage;
    std::shared_ptr<IAttributeUsageListener>             _tracker;

    bool scan_attribute_usage(std::lock_guard<std::mutex>&);
    void notify_attribute_usage(const AttributeUsageStats& attribute_usage);
public:
    AttributeUsageNotifier(std::shared_ptr<IAttributeUsageListener> tracker);
    ~AttributeUsageNotifier();
    void remove_document_type(const std::string& document_type);
    std::unique_ptr<IAttributeUsageListener> make_attribute_usage_listener(const std::string& document_type);
};

}
