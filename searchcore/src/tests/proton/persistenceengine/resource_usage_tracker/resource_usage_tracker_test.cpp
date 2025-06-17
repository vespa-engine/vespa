// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/resource_usage.h>
#include <vespa/persistence/spi/resource_usage_listener.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <vespa/searchcore/proton/persistenceengine/resource_usage_tracker.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <atomic>

using storage::spi::AttributeResourceUsage;
using storage::spi::ResourceUsage;
using proton::test::DiskMemUsageNotifier;
using proton::AttributeUsageStats;
using proton::DiskMemUsageState;
using proton::ResourceUsageTracker;

namespace {

struct MyResourceUsageListener : public storage::spi::ResourceUsageListener
{
    std::atomic<size_t> _update_count;

    MyResourceUsageListener()
        : storage::spi::ResourceUsageListener(),
          _update_count(0u)
    {
    }

    void update_resource_usage(const ResourceUsage& resource_usage) override {
        storage::spi::ResourceUsageListener::update_resource_usage(resource_usage);
        ++_update_count;
    }
    size_t get_update_count() const { return _update_count; }
};

}

class ResourceUsageTrackerTest : public ::testing::Test
{
protected:
    DiskMemUsageNotifier                     _notifier;
    std::shared_ptr<ResourceUsageTracker>    _tracker;
    std::unique_ptr<MyResourceUsageListener> _listener;

public:
    ResourceUsageTrackerTest()
        : testing::Test(),
          _notifier(DiskMemUsageState({ 0.8, 0.5 }, { 0.8, 0.4 })),
          _tracker(std::make_shared<ResourceUsageTracker>(_notifier)),
          _listener(std::make_unique<MyResourceUsageListener>())
    {
    }

    ~ResourceUsageTrackerTest();

    void notify(double disk_usage, double memory_usage, double transient_disk_usage = 0.0, double transient_memory_usage = 0.0)
    {
        _notifier.notify(DiskMemUsageState({ 0.8, disk_usage }, { 0.8, memory_usage },
                                           transient_disk_usage, transient_memory_usage));
    }

    ResourceUsage get_usage() { return _listener->get_usage(); }
    size_t get_update_count() const { return _listener->get_update_count(); }
};

ResourceUsageTrackerTest::~ResourceUsageTrackerTest() = default;

TEST_F(ResourceUsageTrackerTest, resource_usage_is_forwarded_to_listener)
{
    EXPECT_EQ(ResourceUsage(0.0, 0.0), get_usage());
    auto register_guard = _tracker->set_listener(*_listener);
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_usage());
    notify(0.75, 0.25);
    EXPECT_EQ(ResourceUsage(0.75, 0.25), get_usage());
}

TEST_F(ResourceUsageTrackerTest, transient_resource_usage_is_subtracted_from_absolute_usage)
{
    auto register_guard = _tracker->set_listener(*_listener);
    notify(0.8, 0.5, 0.4, 0.2);
    EXPECT_EQ(ResourceUsage(0.4, 0.3), get_usage());
    notify(0.8, 0.5, 0.9, 0.6);
    EXPECT_EQ(ResourceUsage(0.0, 0.0), get_usage());
}

TEST_F(ResourceUsageTrackerTest, forwarding_depends_on_register_guard)
{
    auto register_guard = _tracker->set_listener(*_listener);
    register_guard.reset();
    notify(0.75, 0.25);
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_usage());
}

TEST_F(ResourceUsageTrackerTest, no_forwarding_to_deleted_listener)
{
    _listener->set_register_guard(_tracker->set_listener(*_listener));
    notify(0.75, 0.25);
    EXPECT_EQ(ResourceUsage(0.75, 0.25), get_usage());
    _listener.reset();
    notify(0.2, 0.1);
}

TEST_F(ResourceUsageTrackerTest, register_guard_handles_deleted_tracker)
{
    auto register_guard = _tracker->set_listener(*_listener);
    _tracker.reset();
}

namespace {

constexpr size_t usage_limit = 1024;

double rel_usage(size_t usage) noexcept {
    return (double) usage / (double) usage_limit;
}

ResourceUsage make_resource_usage(const std::string& attr_name, size_t used_address_space)
{
    AttributeResourceUsage address_space_usage(rel_usage(used_address_space), attr_name);
    return ResourceUsage(0.0, 0.0, address_space_usage);
}

AttributeUsageStats make_stats(const std::string& document_type, const std::string& subdb,
                               const std::string& attribute,
                               size_t used_address_space)
{
    AttributeUsageStats stats(document_type);
    if (!document_type.empty()) {
        search::AddressSpaceUsage usage;
        usage.set("comp", vespalib::AddressSpace(used_address_space, 0, usage_limit));
        stats.merge(usage, attribute, subdb);
    }
    return stats;
}

}

TEST_F(ResourceUsageTrackerTest, attribute_usage_is_sent_to_listener)
{
    notify(0.0, 0.0);
    auto register_guard = _tracker->set_listener(*_listener);
    _tracker->notify_attribute_usage(make_stats("doctype2", "0.ready", "a1", 15));
    EXPECT_EQ(make_resource_usage("doctype2.0.ready.a1.comp", 15), get_usage());
    EXPECT_EQ(2, get_update_count());
    _tracker->notify_attribute_usage(make_stats("doctype1", "2.notready", "a1", 16));
    EXPECT_EQ(make_resource_usage("doctype1.2.notready.a1.comp", 16), get_usage());
    EXPECT_EQ(3, get_update_count());
    _tracker->notify_attribute_usage(make_stats("doctype2", "0.ready", "a1", 15));
    EXPECT_EQ(make_resource_usage("doctype2.0.ready.a1.comp", 15), get_usage());
    EXPECT_EQ(4, get_update_count());
    _tracker->notify_attribute_usage(make_stats("doctype1", "0.ready", "a1", 10));
    EXPECT_EQ(make_resource_usage("doctype1.0.ready.a1.comp", 10), get_usage());
    EXPECT_EQ(5, get_update_count());
    _tracker->notify_attribute_usage(make_stats("", "", "", 10));
    EXPECT_EQ(make_resource_usage("", 0), get_usage());
    EXPECT_EQ(6, get_update_count());
    _tracker->notify_attribute_usage(make_stats("doctype2", "0.ready", "a1", 15));
    EXPECT_EQ(make_resource_usage("doctype2.0.ready.a1.comp", 15), get_usage());
    EXPECT_EQ(7, get_update_count());
}

GTEST_MAIN_RUN_ALL_TESTS()
