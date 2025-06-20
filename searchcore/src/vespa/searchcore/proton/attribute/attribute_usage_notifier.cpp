// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_notifier.h"
#include "i_attribute_usage_listener.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace proton {

namespace {

class MaxAttributeUsage
{
    const AttributeUsageStats* _max;

public:
    MaxAttributeUsage() noexcept
        : _max(nullptr)
    {
    }

    void sample(const AttributeUsageStats& usage) noexcept {
        if (_max == nullptr || _max->less_usage_than(usage)) {
            _max = &usage;
        }
    }

    AttributeUsageStats get_max_usage() const {
        return (_max != nullptr) ? *_max : AttributeUsageStats();
    }
};

}

class AttributeUsageNotifier::AttributeUsageListener : public IAttributeUsageListener
{
    std::shared_ptr<AttributeUsageNotifier> _notifier;
    std::string                             _document_type;

public:
    AttributeUsageListener(std::shared_ptr<AttributeUsageNotifier> notifier, const std::string& document_type);

    ~AttributeUsageListener() override;
    void notify_attribute_usage(const AttributeUsageStats &attribute_usage) override;
};

AttributeUsageNotifier::AttributeUsageListener::AttributeUsageListener(std::shared_ptr<AttributeUsageNotifier> notifier, const std::string &document_type)
    : IAttributeUsageListener(),
      _notifier(std::move(notifier)),
      _document_type(document_type)
{
}

AttributeUsageNotifier::AttributeUsageListener::~AttributeUsageListener()
{
    _notifier->remove_document_type(_document_type);
}

void
AttributeUsageNotifier::AttributeUsageListener::notify_attribute_usage(const AttributeUsageStats &attribute_usage)
{
    _notifier->notify_attribute_usage(attribute_usage);
}

AttributeUsageNotifier::AttributeUsageNotifier(std::shared_ptr<IAttributeUsageListener> resource_usage_notifier)
  : _lock(),
    _attribute_usage(),
    _max_attribute_usage(),
    _resource_usage_notifier(resource_usage_notifier),
    _closed(false)
{
}

AttributeUsageNotifier::~AttributeUsageNotifier() = default;

bool
AttributeUsageNotifier::scan_attribute_usage(std::lock_guard<std::mutex>&)
{
    MaxAttributeUsage address_space_max;
    for (const auto& kv : _attribute_usage) {
        address_space_max.sample(kv.second);
    }
    auto new_max_attribute_usage = address_space_max.get_max_usage();
    bool changed = _max_attribute_usage != new_max_attribute_usage;
    if (changed) {
        _max_attribute_usage = std::move(new_max_attribute_usage);
    }
    return changed;
}

void
AttributeUsageNotifier::remove_document_type(const std::string& document_type)
{
    std::lock_guard guard(_lock);
    _attribute_usage.erase(document_type);
    if (_max_attribute_usage.document_type() != document_type) {
        return;
    }
    if (scan_attribute_usage(guard)) {
        notify_attribute_usage();
    }
}

void
AttributeUsageNotifier::notify_attribute_usage(const AttributeUsageStats& attribute_usage)
{
    std::lock_guard guard(_lock);
    auto& old_usage = _attribute_usage[attribute_usage.document_type()];
    if (old_usage.max_address_space_usage() == attribute_usage.max_address_space_usage()) {
        return; // usage for document type has not changed
    }
    old_usage = attribute_usage;
    if (attribute_usage.document_type() == _max_attribute_usage.document_type() ||
        _max_attribute_usage.less_usage_than(attribute_usage)) {
        if (scan_attribute_usage(guard)) {
            notify_attribute_usage();
        }
    }
}

void
AttributeUsageNotifier::notify_attribute_usage()
{
    if (!_closed) {
        if (_resource_usage_notifier) {
            _resource_usage_notifier->notify_attribute_usage(_max_attribute_usage);
        }
    }
}

std::unique_ptr<IAttributeUsageListener>
AttributeUsageNotifier::make_attribute_usage_listener(const std::string &document_type)
{
    return std::make_unique<AttributeUsageListener>(shared_from_this(), document_type);
}

void
AttributeUsageNotifier::close()
{
    std::lock_guard guard(_lock);
    _closed = true;
}

}
