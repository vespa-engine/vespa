// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_notifier.h"

#include "i_attribute_usage_and_load_info_listener.h"
#include "i_attribute_usage_listener.h"

#include <vespa/vespalib/stllike/hash_map.hpp>

namespace proton {

namespace {

class MaxAttributeUsage {
    const AttributeUsageStats* _max;

public:
    MaxAttributeUsage() noexcept : _max(nullptr) {}

    void sample(const AttributeUsageStats& usage) noexcept {
        if (_max == nullptr || _max->less_usage_than(usage)) {
            _max = &usage;
        }
    }

    AttributeUsageStats get_max_usage() const { return (_max != nullptr) ? *_max : AttributeUsageStats(); }
};

} // namespace

class AttributeUsageNotifier::AttributeUsageListener : public IAttributeUsageAndLoadInfoListener {
    std::shared_ptr<AttributeUsageNotifier> _notifier;
    std::string                             _document_type;

public:
    AttributeUsageListener(std::shared_ptr<AttributeUsageNotifier> notifier, const std::string& document_type);

    ~AttributeUsageListener() override;
    void notify_attribute_usage(AttributeUsageStatsAndLoadInfo attribute_usage_and_load_info) noexcept override;
};

AttributeUsageNotifier::AttributeUsageListener::AttributeUsageListener(
    std::shared_ptr<AttributeUsageNotifier> notifier, const std::string& document_type)
    : IAttributeUsageAndLoadInfoListener(), _notifier(std::move(notifier)), _document_type(document_type) {
}

AttributeUsageNotifier::AttributeUsageListener::~AttributeUsageListener() {
    _notifier->remove_document_type(_document_type);
}

void AttributeUsageNotifier::AttributeUsageListener::notify_attribute_usage(
    AttributeUsageStatsAndLoadInfo attribute_usage_and_load_info) noexcept {
    _notifier->notify_attribute_usage(std::move(attribute_usage_and_load_info));
}

AttributeUsageNotifier::AttributeUsageNotifier(std::shared_ptr<IAttributeUsageListener> resource_usage_notifier)
    : _lock(),
      _attribute_usage(),
      _max_attribute_usage(),
      _resource_usage_notifier(std::move(resource_usage_notifier)),
      _closed(false) {
}

AttributeUsageNotifier::~AttributeUsageNotifier() = default;

bool AttributeUsageNotifier::scan_attribute_usage(std::lock_guard<std::mutex>&) {
    MaxAttributeUsage address_space_max;
    for (const auto& kv : _attribute_usage) {
        address_space_max.sample(kv.second.usage_stats());
    }
    auto new_max_attribute_usage = address_space_max.get_max_usage();
    bool changed = _max_attribute_usage != new_max_attribute_usage;
    if (changed) {
        _max_attribute_usage = std::move(new_max_attribute_usage);
    }
    return changed;
}

void AttributeUsageNotifier::remove_document_type(const std::string& document_type) noexcept {
    std::lock_guard guard(_lock);
    _attribute_usage.erase(document_type);
    if (_max_attribute_usage.document_type() != document_type) {
        return;
    }
    if (scan_attribute_usage(guard)) {
        notify_attribute_usage();
    }
}

void AttributeUsageNotifier::notify_attribute_usage(
    AttributeUsageStatsAndLoadInfo attribute_usage_and_load_info) noexcept {
    std::lock_guard guard(_lock);
    const auto      attribute_usage = attribute_usage_and_load_info.usage_stats();
    auto&           old_usage = _attribute_usage[attribute_usage.document_type()];
    bool            unnchanged_max_address_space_usage =
        old_usage.usage_stats().max_address_space_usage() == attribute_usage.max_address_space_usage();
    old_usage = std::move(attribute_usage_and_load_info);
    if (unnchanged_max_address_space_usage) {
        return; // usage for document type has not changed
    }
    if (attribute_usage.document_type() == _max_attribute_usage.document_type() ||
        _max_attribute_usage.less_usage_than(attribute_usage))
    {
        if (scan_attribute_usage(guard)) {
            notify_attribute_usage();
        }
    }
}

void AttributeUsageNotifier::notify_attribute_usage() {
    if (!_closed) {
        if (_resource_usage_notifier) {
            _resource_usage_notifier->notify_attribute_usage(_max_attribute_usage);
        }
    }
}

std::unique_ptr<IAttributeUsageAndLoadInfoListener>
AttributeUsageNotifier::make_attribute_usage_listener(const std::string& document_type) {
    return std::make_unique<AttributeUsageListener>(shared_from_this(), document_type);
}

void AttributeUsageNotifier::close() {
    std::lock_guard guard(_lock);
    _closed = true;
}

} // namespace proton
