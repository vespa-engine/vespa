// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_usage_notifier.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <atomic>

using proton::AttributeUsageNotifier;
using proton::AttributeUsageStats;
using proton::IAttributeUsageListener;
using vespalib::AddressSpace;

namespace {

struct MyAttributeUsageListener : public IAttributeUsageListener
{
    mutable std::mutex  _lock;
    size_t              _update_count;
    AttributeUsageStats _usage;

    MyAttributeUsageListener()
        : IAttributeUsageListener(),
          _lock(),
          _update_count(0u),
          _usage()
    {
    }

    void notify_attribute_usage(const AttributeUsageStats& attribute_usage) override {
        std::lock_guard guard(_lock);
        _usage = attribute_usage;
        ++_update_count;
    }
    size_t get_update_count() const {
        std::lock_guard guard(_lock);
        return _update_count;
    }
    AttributeUsageStats get_usage() const {
        std::lock_guard guard(_lock);
        return _usage;
    }
};

}

class AttributeUsageNotifierTest : public ::testing::Test
{
protected:
    std::shared_ptr<MyAttributeUsageListener> _listener;
    std::shared_ptr<AttributeUsageNotifier>   _notifier;

public:
    AttributeUsageNotifierTest()
        : testing::Test(),
          _listener(std::make_shared<MyAttributeUsageListener>()),
          _notifier(std::make_shared<AttributeUsageNotifier>(_listener))
    {
    }

    ~AttributeUsageNotifierTest();

    AttributeUsageStats get_usage() { return _listener->get_usage(); }
    size_t get_update_count() const { return _listener->get_update_count(); }
};

AttributeUsageNotifierTest::~AttributeUsageNotifierTest() = default;

namespace {

struct NamedAttribute
{
    std::string subdb;
    std::string attribute;

    NamedAttribute(const std::string& subdb_in, const std::string& attribute_in)
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

    AttributeUsageStatsBuilder(const std::string& document_type)
        : stats(document_type)
    {
    }

    ~AttributeUsageStatsBuilder();

    AttributeUsageStatsBuilder& reset() {
        std::string document_type = stats.document_type();
        stats = AttributeUsageStats(document_type);
        return *this;
    }
    AttributeUsageStatsBuilder& merge(const NamedAttribute& named_attribute, size_t used_address_space);

    AttributeUsageStats build() { return stats; }

};

AttributeUsageStatsBuilder::~AttributeUsageStatsBuilder() = default;

AttributeUsageStatsBuilder&
AttributeUsageStatsBuilder::merge(const NamedAttribute& named_attribute, size_t used_address_space)
{
    AddressSpace address_space_usage(used_address_space, 0, usage_limit);
    search::AddressSpaceUsage as_usage;
    as_usage.set("comp", address_space_usage);
    stats.merge(as_usage, named_attribute.attribute, named_attribute.subdb);
    return *this;
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

TEST_F(AttributeUsageNotifierTest, aggregates_attribute_usage)
{
    auto aul1 = _notifier->make_attribute_usage_listener("doctype1");
    auto aul2 = _notifier->make_attribute_usage_listener("doctype2");
    AttributeUsageStatsBuilder b1("doctype1");
    AttributeUsageStatsBuilder b2("doctype2");
    b1.merge(ready_a1, 10).merge(ready_a2, 5);
    b2.merge(ready_a1, 15);
    aul1->notify_attribute_usage(b1.build());
    aul2->notify_attribute_usage(b2.build());
    EXPECT_EQ(make_stats("doctype2", "0.ready", "a1", 15), get_usage());
    b1.merge(notready_a1, 16);
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_stats("doctype1", "2.notready", "a1", 16), get_usage());
    b1.reset().merge(ready_a1, 10).merge(ready_a2, 5);
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_stats("doctype2", "0.ready", "a1", 15), get_usage());
    aul2.reset();
    EXPECT_EQ(make_stats("doctype1", "0.ready", "a1", 10), get_usage());
    aul1.reset();
    EXPECT_EQ(make_stats("", "", "", 0), get_usage());
    aul2 = _notifier->make_attribute_usage_listener("doctype2");
    aul2->notify_attribute_usage(b2.build());
    EXPECT_EQ(make_stats("doctype2", "0.ready", "a1", 15), get_usage());
}

TEST_F(AttributeUsageNotifierTest, can_skip_scan_when_aggregating_attributes)
{
    auto aul1 = _notifier->make_attribute_usage_listener("doctype1");
    auto aul2 = _notifier->make_attribute_usage_listener("doctype2");
    AttributeUsageStatsBuilder b1("doctype1");
    AttributeUsageStatsBuilder b2("doctype2");
    b1.merge(ready_a1, 20).merge(ready_a2, 5);
    b2.merge(ready_a1, 15);
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_stats("doctype1", "0.ready", "a1", 20), get_usage());
    EXPECT_EQ(1u, get_update_count());
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_stats("doctype1", "0.ready", "a1", 20), get_usage());
    EXPECT_EQ(1u, get_update_count()); // usage for doctype1 has not changed
    aul2->notify_attribute_usage(b2.build());
    EXPECT_EQ(make_stats("doctype1", "0.ready", "a1", 20), get_usage());
    EXPECT_EQ(1u, get_update_count()); // usage for doctype2 is less than usage for doctype1
    aul2.reset();
    EXPECT_EQ(1u, get_update_count()); // no notify
    aul1.reset();
    EXPECT_EQ(2u, get_update_count()); // notify
    EXPECT_EQ(make_stats("", "", "", 0), get_usage());
}

GTEST_MAIN_RUN_ALL_TESTS()
