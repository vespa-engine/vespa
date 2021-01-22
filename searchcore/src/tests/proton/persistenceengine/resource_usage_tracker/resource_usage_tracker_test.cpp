// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/resource_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchcore/proton/persistenceengine/resource_usage_tracker.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
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

    void notify(double disk_usage, double memory_usage)
    {
        _notifier.notify(DiskMemUsageState({ 0.8, disk_usage }, { 0.8, memory_usage }));
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

struct NamedAttribute
{
    vespalib::string subdb;
    vespalib::string attribute;

    NamedAttribute(const vespalib::string& subdb_in, const vespalib::string& attribute_in)
        : subdb(subdb_in),
          attribute(attribute_in)
    {
    }
};

NamedAttribute ready_a1("0.ready", "a1");
NamedAttribute notready_a1("2.notready", "a1");
NamedAttribute ready_a2("0.ready", "a2");

constexpr size_t usage_limit = 1024;

struct AttributeUsageStatsBuilder
{
    AttributeUsageStats stats;

    AttributeUsageStatsBuilder()
        : stats()
    {
    }

    ~AttributeUsageStatsBuilder();

    AttributeUsageStatsBuilder& reset() { stats = AttributeUsageStats(); return *this; }
    AttributeUsageStatsBuilder& merge(const NamedAttribute& named_attribute, size_t used_enum_store, size_t used_multivalue);

    AttributeUsageStats build() { return stats; }

};

AttributeUsageStatsBuilder::~AttributeUsageStatsBuilder() = default;

AttributeUsageStatsBuilder&
AttributeUsageStatsBuilder::merge(const NamedAttribute& named_attribute, size_t used_enum_store, size_t used_multivalue)
{
    vespalib::AddressSpace enum_store_usage(used_enum_store, 0, usage_limit);
    vespalib::AddressSpace multivalue_usage(used_multivalue, 0, usage_limit);
    search::AddressSpaceUsage as_usage(enum_store_usage, multivalue_usage);
    stats.merge(as_usage, named_attribute.attribute, named_attribute.subdb);
    return *this;
}

double rel_usage(size_t usage) noexcept {
    return (double) usage / (double) usage_limit;
}

ResourceUsage make_resource_usage(const vespalib::string& enum_store_name, size_t used_enum_store, const vespalib::string &multivalue_name, size_t used_multivalue)
{
    AttributeResourceUsage enum_store_usage(rel_usage(used_enum_store), enum_store_name);
    AttributeResourceUsage multivalue_usage(rel_usage(used_multivalue), multivalue_name);
    return ResourceUsage(0.0, 0.0, enum_store_usage, multivalue_usage);
}

}

TEST_F(ResourceUsageTrackerTest, aggregates_attribute_usage)
{
    notify(0.0, 0.0);
    auto register_guard = _tracker->set_listener(*_listener);
    auto aul1 = _tracker->make_attribute_usage_listener("doctype1");
    auto aul2 = _tracker->make_attribute_usage_listener("doctype2");
    AttributeUsageStatsBuilder b1;
    AttributeUsageStatsBuilder b2;
    b1.merge(ready_a1, 10, 20).merge(ready_a2, 5, 30);
    b2.merge(ready_a1, 15, 15);
    aul1->notify_attribute_usage(b1.build());
    aul2->notify_attribute_usage(b2.build());
    EXPECT_EQ(make_resource_usage("doctype2.0.ready.a1", 15, "doctype1.0.ready.a2", 30), get_usage());
    b1.merge(notready_a1, 5, 31);
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_resource_usage("doctype2.0.ready.a1", 15, "doctype1.2.notready.a1", 31), get_usage());
    b1.reset().merge(ready_a1, 10, 20).merge(ready_a2, 5, 30);
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_resource_usage("doctype2.0.ready.a1", 15, "doctype1.0.ready.a2", 30), get_usage());
    aul2.reset();
    EXPECT_EQ(make_resource_usage("doctype1.0.ready.a1", 10, "doctype1.0.ready.a2", 30), get_usage());
    aul1.reset();
    EXPECT_EQ(make_resource_usage("", 0, "", 0), get_usage());
    aul2 = _tracker->make_attribute_usage_listener("doctype2");
    aul2->notify_attribute_usage(b2.build());
    EXPECT_EQ(make_resource_usage("doctype2.0.ready.a1", 15, "doctype2.0.ready.a1", 15), get_usage());
}

TEST_F(ResourceUsageTrackerTest, can_skip_scan_when_aggregating_attributes)
{
    notify(0.0, 0.0);
    auto register_guard = _tracker->set_listener(*_listener);
    auto aul1 = _tracker->make_attribute_usage_listener("doctype1");
    auto aul2 = _tracker->make_attribute_usage_listener("doctype2");
    AttributeUsageStatsBuilder b1;
    AttributeUsageStatsBuilder b2;
    b1.merge(ready_a1, 20, 20).merge(ready_a2, 5, 30);
    b2.merge(ready_a1, 15, 15);
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_resource_usage("doctype1.0.ready.a1", 20, "doctype1.0.ready.a2", 30), get_usage());
    EXPECT_EQ(2u, get_update_count());
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_resource_usage("doctype1.0.ready.a1", 20, "doctype1.0.ready.a2", 30), get_usage());
    EXPECT_EQ(2u, get_update_count()); // usage for doctype1 has not changed
    aul2->notify_attribute_usage(b2.build());
    EXPECT_EQ(make_resource_usage("doctype1.0.ready.a1", 20, "doctype1.0.ready.a2", 30), get_usage());
    EXPECT_EQ(2u, get_update_count()); // usage for doctype2 is less than usage for doctype1
    aul2.reset();
    aul1.reset();
    EXPECT_EQ(4u, get_update_count()); // never skip scan when removing document type
}

GTEST_MAIN_RUN_ALL_TESTS()
