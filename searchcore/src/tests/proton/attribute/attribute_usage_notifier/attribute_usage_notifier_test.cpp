// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_usage_notifier.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_stats_and_load_info.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_and_load_info_listener.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::AttributeUsageNotifier;
using proton::AttributeUsageStats;
using proton::AttributeUsageStatsAndLoadInfo;
using proton::IAttributeUsageAndLoadInfoListener;
using proton::IAttributeUsageListener;
using proton::initializer::LoadMemoryUsage;
using vespalib::AddressSpace;

namespace {

struct MyAttributeUsageListener : public IAttributeUsageListener {
    mutable std::mutex  _lock;
    size_t              _update_count;
    AttributeUsageStats _usage;
    size_t              _reserved_memory_for_attribute_load;

    MyAttributeUsageListener() : IAttributeUsageListener(), _lock(), _update_count(0u), _usage() {}

    void notify_attribute_usage(const AttributeUsageStats& attribute_usage,
                                size_t                     reserved_memory_for_attribute_load) override {
        std::lock_guard guard(_lock);
        _usage = attribute_usage;
        _reserved_memory_for_attribute_load = reserved_memory_for_attribute_load;
        ++_update_count;
    }
    [[nodiscard]] size_t get_update_count() const {
        std::lock_guard guard(_lock);
        return _update_count;
    }
    [[nodiscard]] AttributeUsageStats get_usage() const {
        std::lock_guard guard(_lock);
        return _usage;
    }
    [[nodiscard]] size_t get_reserved_memory_for_attribute_load() const {
        std::lock_guard guard(_lock);
        return _reserved_memory_for_attribute_load;
    }
};

} // namespace

class AttributeUsageNotifierTest : public ::testing::Test {
protected:
    std::shared_ptr<MyAttributeUsageListener> _listener;
    std::shared_ptr<AttributeUsageNotifier>   _notifier;

public:
    AttributeUsageNotifierTest()
        : testing::Test(),
          _listener(std::make_shared<MyAttributeUsageListener>()),
          _notifier(std::make_shared<AttributeUsageNotifier>(_listener, 0)) {}

    ~AttributeUsageNotifierTest() override;

    [[nodiscard]] AttributeUsageStats get_usage() { return _listener->get_usage(); }
    [[nodiscard]] size_t get_reserved_memory_for_attribute_load() {
        return _listener->get_reserved_memory_for_attribute_load();
    }
    [[nodiscard]] size_t get_update_count() const { return _listener->get_update_count(); }
};

AttributeUsageNotifierTest::~AttributeUsageNotifierTest() = default;

namespace {

struct NamedAttribute {
    AttributeUsageStatsAndLoadInfo::SubDb sub_db_enum;
    std::string                           subdb;
    std::string                           attribute;

    NamedAttribute(AttributeUsageStatsAndLoadInfo::SubDb sub_db_enum_in, const std::string& subdb_in,
                   const std::string& attribute_in)
        : sub_db_enum(sub_db_enum_in), subdb(subdb_in), attribute(attribute_in) {}
};

NamedAttribute ready_a1(AttributeUsageStatsAndLoadInfo::SubDb::READY, "0.ready", "a1");
NamedAttribute notready_a1(AttributeUsageStatsAndLoadInfo::SubDb::NOTREADY, "2.notready", "a1");
NamedAttribute ready_a2(AttributeUsageStatsAndLoadInfo::SubDb::READY, "0.ready", "a2");

constexpr size_t usage_limit = 1024;

struct AttributeUsageStatsBuilder {
    AttributeUsageStatsAndLoadInfo stats;

    AttributeUsageStatsBuilder(const std::string& document_type) : stats(document_type, 0, 0) {}

    ~AttributeUsageStatsBuilder();

    AttributeUsageStatsBuilder& reset() {
        std::string document_type = stats.usage_stats().document_type();
        stats = AttributeUsageStatsAndLoadInfo(document_type, 0, 0);
        return *this;
    }
    AttributeUsageStatsBuilder& merge(const NamedAttribute& named_attribute, size_t used_address_space);
    AttributeUsageStatsBuilder& merge(const NamedAttribute& named_attribute, size_t used_address_space,
                                      const LoadMemoryUsage& load_memory_usage);

    AttributeUsageStatsAndLoadInfo build() { return stats.clone(); }
};

AttributeUsageStatsBuilder::~AttributeUsageStatsBuilder() = default;

AttributeUsageStatsBuilder& AttributeUsageStatsBuilder::merge(const NamedAttribute& named_attribute,
                                                              size_t                used_address_space) {
    return merge(named_attribute, used_address_space, LoadMemoryUsage());
}

AttributeUsageStatsBuilder& AttributeUsageStatsBuilder::merge(const NamedAttribute&  named_attribute,
                                                              size_t                 used_address_space,
                                                              const LoadMemoryUsage& load_memory_usage) {
    AddressSpace              address_space_usage(used_address_space, 0, usage_limit);
    search::AddressSpaceUsage as_usage;
    as_usage.set("comp", address_space_usage);
    stats.merge(as_usage, load_memory_usage, named_attribute.sub_db_enum, named_attribute.attribute,
                named_attribute.subdb);
    return *this;
}

AttributeUsageStats make_stats(const std::string& document_type, const std::string& subdb,
                               const std::string& attribute, size_t used_address_space) {
    AttributeUsageStats stats(document_type);
    if (!document_type.empty()) {
        search::AddressSpaceUsage usage;
        usage.set("comp", vespalib::AddressSpace(used_address_space, 0, usage_limit));
        stats.merge(usage, attribute, subdb);
    }
    return stats;
}

} // namespace

TEST_F(AttributeUsageNotifierTest, aggregates_attribute_usage) {
    auto                       aul1 = _notifier->make_attribute_usage_listener("doctype1");
    auto                       aul2 = _notifier->make_attribute_usage_listener("doctype2");
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

TEST_F(AttributeUsageNotifierTest, can_skip_scan_when_aggregating_attributes) {
    auto                       aul1 = _notifier->make_attribute_usage_listener("doctype1");
    auto                       aul2 = _notifier->make_attribute_usage_listener("doctype2");
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

TEST_F(AttributeUsageNotifierTest, passes_reserved_memory_for_attribute_load) {
    auto                       aul1 = _notifier->make_attribute_usage_listener("doctype1");
    AttributeUsageStatsBuilder b1("doctype1");
    b1.merge(ready_a1, 20, LoadMemoryUsage(10, 5)).merge(ready_a2, 5, LoadMemoryUsage(1, 2));
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_stats("doctype1", "0.ready", "a1", 20), get_usage());
    EXPECT_EQ(8, get_reserved_memory_for_attribute_load()); // a1 is loaded before a2
    EXPECT_EQ(1u, get_update_count());
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(1u, get_update_count());
    _notifier->apply_config(2); // 2 initializer threads
    EXPECT_EQ(1u, get_update_count());
    aul1->notify_attribute_usage(b1.build());
    EXPECT_EQ(make_stats("doctype1", "0.ready", "a1", 20), get_usage());
    EXPECT_EQ(11, get_reserved_memory_for_attribute_load()); // a1 and a2 are loaded at the same time
    EXPECT_EQ(2u, get_update_count());
}

GTEST_MAIN_RUN_ALL_TESTS()
