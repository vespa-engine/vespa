// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_tracker.h"
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchcore/proton/server/disk_mem_usage_state.h>
#include <vespa/searchcore/proton/server/i_disk_mem_usage_notifier.h>
#include <vespa/persistence/spi/i_resource_usage_listener.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

using storage::spi::AttributeResourceUsage;
using storage::spi::ResourceUsage;

namespace proton {

class ResourceUsageTracker::ListenerGuard : public vespalib::IDestructorCallback
{
    std::weak_ptr<ResourceUsageTracker> _tracker;
public:
    ListenerGuard(std::shared_ptr<ResourceUsageTracker> tracker);
    ~ListenerGuard() override;
};

ResourceUsageTracker::ListenerGuard::ListenerGuard(std::shared_ptr<ResourceUsageTracker> tracker)
    : _tracker(tracker)
{
}

ResourceUsageTracker::ListenerGuard::~ListenerGuard()
{
    auto tracker = _tracker.lock();
    if (tracker) {
        tracker->remove_listener();
    }
}

class ResourceUsageTracker::AttributeUsageListener : public IAttributeUsageListener
{
    std::shared_ptr<ResourceUsageTracker> _tracker;
    vespalib::string                      _document_type;

public:
    AttributeUsageListener(std::shared_ptr<ResourceUsageTracker> tracker, const vespalib::string& document_type);

    ~AttributeUsageListener() override;
    void notify_attribute_usage(const AttributeUsageStats &attribute_usage) override;
};

ResourceUsageTracker::AttributeUsageListener::AttributeUsageListener(std::shared_ptr<ResourceUsageTracker> tracker, const vespalib::string &document_type)
    : IAttributeUsageListener(),
      _tracker(std::move(tracker)),
      _document_type(document_type)
{
}

ResourceUsageTracker::AttributeUsageListener::~AttributeUsageListener()
{
    _tracker->remove_document_type(_document_type);
}

void
ResourceUsageTracker::AttributeUsageListener::notify_attribute_usage(const AttributeUsageStats &attribute_usage)
{
    _tracker->notify_attribute_usage(_document_type, attribute_usage);
}

ResourceUsageTracker::ResourceUsageTracker(IDiskMemUsageNotifier& disk_mem_usage_notifier)
    : std::enable_shared_from_this<ResourceUsageTracker>(),
      IDiskMemUsageListener(),
      _lock(),
      _resource_usage(),
      _listener(nullptr),
      _disk_mem_usage_notifier(disk_mem_usage_notifier),
      _attribute_usage(),
      _attribute_enum_store_max_document_type(),
      _attribute_multivalue_max_document_type()
{
    _disk_mem_usage_notifier.addDiskMemUsageListener(this);
}

ResourceUsageTracker::~ResourceUsageTracker()
{
    _disk_mem_usage_notifier.removeDiskMemUsageListener(this);
}

void
ResourceUsageTracker::notifyDiskMemUsage(DiskMemUsageState state)
{
    std::lock_guard guard(_lock);
    _resource_usage = ResourceUsage(state.diskState().usage(), state.memoryState().usage(), _resource_usage.get_attribute_enum_store_usage(), _resource_usage.get_attribute_multivalue_usage());
    if (_listener != nullptr) {
        _listener->update_resource_usage(_resource_usage);
    }
}

std::unique_ptr<vespalib::IDestructorCallback>
ResourceUsageTracker::set_listener(storage::spi::IResourceUsageListener& listener)
{
    std::lock_guard guard(_lock);
    assert(_listener == nullptr);
    _listener = &listener;
    listener.update_resource_usage(_resource_usage);
    return std::make_unique<ListenerGuard>(shared_from_this());
}

void
ResourceUsageTracker::remove_listener()
{
    std::lock_guard guard(_lock);
    _listener = nullptr;
}

void
ResourceUsageTracker::remove_document_type(const vespalib::string &document_type)
{
    std::lock_guard guard(_lock);
    _attribute_usage.erase(document_type);
    if (scan_attribute_usage(true, guard) && _listener != nullptr) {
        _listener->update_resource_usage(_resource_usage);
    }
}

namespace {

bool same_usage(const AddressSpaceUsageStats &lhs, const AddressSpaceUsageStats &rhs) {
    return ((lhs.getUsage().usage() == rhs.getUsage().usage()) &&
            (lhs.getAttributeName() == rhs.getAttributeName()) &&
            (lhs.getSubDbName() == rhs.getSubDbName()));
}

bool can_skip_scan(double max, double old_max, bool same_document_type) noexcept {
    return (!same_document_type && (max <= old_max));
}

}

void
ResourceUsageTracker::notify_attribute_usage(const vespalib::string &document_type, const AttributeUsageStats &attribute_usage)
{
    std::lock_guard guard(_lock);
    auto& old_usage = _attribute_usage[document_type];
    if (same_usage(old_usage.enumStoreUsage(), attribute_usage.enumStoreUsage()) && 
        same_usage(old_usage.multiValueUsage(), attribute_usage.multiValueUsage())) {
        return; // usage for document type has not changed
    }
    old_usage = attribute_usage;
    double enum_store_max = attribute_usage.enumStoreUsage().getUsage().usage();
    double multivalue_max = attribute_usage.multiValueUsage().getUsage().usage();
    double old_enum_store_max = _resource_usage.get_attribute_enum_store_usage().get_usage();
    double old_multivalue_max = _resource_usage.get_attribute_multivalue_usage().get_usage();

    if (can_skip_scan(enum_store_max, old_enum_store_max, document_type == _attribute_enum_store_max_document_type) &&
        can_skip_scan(multivalue_max, old_multivalue_max, document_type == _attribute_multivalue_max_document_type)) {
        return; // usage for document type is less than or equal to usage for other document types
    }
    if (scan_attribute_usage(false, guard) && _listener != nullptr) {
        _listener->update_resource_usage(_resource_usage);
    }
}

namespace {

class MaxAttributeUsage
{
    const AddressSpaceUsageStats* _max;
    const vespalib::string*       _document_type;
    double                        _max_usage;

    vespalib::string get_name() const {
        return *_document_type + "." + _max->getSubDbName() + "." + _max->getAttributeName();
    }

public:
    MaxAttributeUsage()
        : _max(nullptr),
          _document_type(nullptr),
          _max_usage(0.0)
    {
    }

    void sample(const vespalib::string& document_type, const AddressSpaceUsageStats& usage) {
        if (_max == nullptr || usage.getUsage().usage() > _max_usage) {
            _max = &usage;
            _document_type = &document_type;
            _max_usage = usage.getUsage().usage();
        }
    }
    
    AttributeResourceUsage get_max_resource_usage() {
        if (_max != nullptr) {
            return AttributeResourceUsage(_max_usage, get_name());
        } else {
            return AttributeResourceUsage();
        }
    }

    const vespalib::string get_document_type() const { return _document_type != nullptr ? *_document_type : ""; }
};

}

bool
ResourceUsageTracker::scan_attribute_usage(bool force_changed, std::lock_guard<std::mutex>&)
{
    MaxAttributeUsage enum_store_max;
    MaxAttributeUsage multivalue_max;
    for (const auto& kv : _attribute_usage) {
        enum_store_max.sample(kv.first, kv.second.enumStoreUsage());
        multivalue_max.sample(kv.first, kv.second.multiValueUsage());
    }
    ResourceUsage new_resource_usage(_resource_usage.get_disk_usage(),
                                     _resource_usage.get_memory_usage(),
                                     enum_store_max.get_max_resource_usage(),
                                     multivalue_max.get_max_resource_usage());

    bool changed = (new_resource_usage != _resource_usage) ||
                   force_changed;
    if (changed) {
        _resource_usage = std::move(new_resource_usage);
        _attribute_enum_store_max_document_type = enum_store_max.get_document_type();
        _attribute_multivalue_max_document_type = multivalue_max.get_document_type();
    }
    return changed;
}

std::unique_ptr<IAttributeUsageListener>
ResourceUsageTracker::make_attribute_usage_listener(const vespalib::string &document_type)
{
    return std::make_unique<AttributeUsageListener>(shared_from_this(), document_type);
}

};
