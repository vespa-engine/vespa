// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <vespa/searchcore/proton/server/i_disk_mem_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <memory>
#include <vector>

namespace storage::spi { class IResourceUsageListener; }
namespace vespalib { class IDestructorCallback; }

namespace proton {

class DiskMemUsageState;
class IAttributeUsageListener;
class IDiskMemUsageNotifier;

/*
 * Class tracking resource usage for persistence provider.
 */
class ResourceUsageTracker : public std::enable_shared_from_this<ResourceUsageTracker>, public IDiskMemUsageListener
{
    class ListenerGuard;
    class AttributeUsageListener;
    std::mutex                  _lock;
    storage::spi::ResourceUsage _resource_usage;
    storage::spi::IResourceUsageListener* _listener;
    IDiskMemUsageNotifier&      _disk_mem_usage_notifier;
    vespalib::hash_map<vespalib::string, AttributeUsageStats> _attribute_usage;
    vespalib::string            _attribute_enum_store_max_document_type;
    vespalib::string            _attribute_multivalue_max_document_type;
    void remove_listener();
    void remove_document_type(const vespalib::string &document_type);
    void notify_attribute_usage(const vespalib::string &document_type, const AttributeUsageStats &attribute_usage);
    bool scan_attribute_usage(bool force_changed, std::lock_guard<std::mutex>&);
public:
    ResourceUsageTracker(IDiskMemUsageNotifier& notifier);
    ~ResourceUsageTracker() override;
    void notifyDiskMemUsage(DiskMemUsageState state) override;
    std::unique_ptr<vespalib::IDestructorCallback> set_listener(storage::spi::IResourceUsageListener& listener);
    std::unique_ptr<IAttributeUsageListener> make_attribute_usage_listener(const vespalib::string &document_type);
};

}
