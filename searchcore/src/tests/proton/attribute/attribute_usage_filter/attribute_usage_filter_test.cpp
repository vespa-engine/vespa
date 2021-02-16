// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("attribute_usage_filter_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>

using proton::AttributeUsageFilter;
using proton::AttributeUsageStats;
using proton::IAttributeUsageListener;
using search::AddressSpaceUsage;
using vespalib::AddressSpace;

namespace
{

vespalib::AddressSpace enumStoreOverLoad(30_Gi, 0, 32_Gi);

vespalib::AddressSpace multiValueOverLoad(127_Mi, 0, 128_Mi);


class MyAttributeStats : public AttributeUsageStats
{
public:
    void triggerEnumStoreLimit() {
        merge({        enumStoreOverLoad,
                       search::AddressSpaceUsage::defaultMultiValueUsage() },
              "enumeratedName",
              "ready");
    }

    void triggerMultiValueLimit() {
        merge({         search::AddressSpaceUsage::defaultEnumStoreUsage(),
                       multiValueOverLoad },
              "multiValueName",
              "ready");
    }
};

class MyListener : public IAttributeUsageListener {
public:
    AttributeUsageStats stats;
    MyListener() : stats() {}
    void notify_attribute_usage(const AttributeUsageStats &stats_in) override {
        stats = stats_in;
    }
};

struct Fixture
{
    AttributeUsageFilter filter;
    const MyListener* listener;
    using State = AttributeUsageFilter::State;
    using Config = AttributeUsageFilter::Config;

    Fixture()
        : filter(),
          listener()
    {
        auto my_listener = std::make_unique<MyListener>();
        listener = my_listener.get();
        filter.set_listener(std::move(my_listener));
    }

    void testWrite(const vespalib::string &exp) {
        if (exp.empty()) {
            EXPECT_TRUE(filter.acceptWriteOperation());
            State state = filter.getAcceptState();
            EXPECT_TRUE(state.acceptWriteOperation());
            EXPECT_EQUAL(exp, state.message());
        } else {
            EXPECT_FALSE(filter.acceptWriteOperation());
            State state = filter.getAcceptState();
            EXPECT_FALSE(state.acceptWriteOperation());
            EXPECT_EQUAL(exp, state.message());
        }
    }

    void setAttributeStats(const AttributeUsageStats &stats) {
        filter.setAttributeStats(stats);
    }
};

}

TEST_F("Check that default filter allows write", Fixture)
{
    f.testWrite("");
}


TEST_F("Check that enum store limit can be reached", Fixture)
{
    f.filter.setConfig(Fixture::Config(0.8, 1.0));
    MyAttributeStats stats;
    stats.triggerEnumStoreLimit();
    f.setAttributeStats(stats);
    f.testWrite("enumStoreLimitReached: { "
                "action: \""
                "add more content nodes"
                "\", "
                "reason: \""
                "enum store address space used (0.9375) > limit (0.8)"
                "\", "
                "enumStore: { used: 32212254720, dead: 0, limit: 34359738368}, "
                "attributeName: \"enumeratedName\", subdb: \"ready\"}");
}

TEST_F("Check that multivalue limit can be reached", Fixture)
{
    f.filter.setConfig(Fixture::Config(1.0, 0.8));
    MyAttributeStats stats;
    stats.triggerMultiValueLimit();
    f.setAttributeStats(stats);
    f.testWrite("multiValueLimitReached: { "
                "action: \""
                "add more content nodes"
                "\", "
                "reason: \""
                "multiValue address space used (0.992188) > limit (0.8)"
                "\", "
                "multiValue: { used: 133169152, dead: 0, limit: 134217728}, "
                "attributeName: \"multiValueName\", subdb: \"ready\"}");
}

TEST_F("Check that both enumstore limit and multivalue limit can be reached",
       Fixture)
{
    f.filter.setConfig(Fixture::Config(0.8, 0.8));
    MyAttributeStats stats;
    stats.triggerEnumStoreLimit();
    stats.triggerMultiValueLimit();
    f.setAttributeStats(stats);
    f.testWrite("enumStoreLimitReached: { "
                "action: \""
                "add more content nodes"
                "\", "
                "reason: \""
                "enum store address space used (0.9375) > limit (0.8)"
                "\", "
                "enumStore: { used: 32212254720, dead: 0, limit: 34359738368}, "
                "attributeName: \"enumeratedName\", subdb: \"ready\"}"
                ", "
                "multiValueLimitReached: { "
                "action: \""
                "add more content nodes"
                "\", "
                "reason: \""
                "multiValue address space used (0.992188) > limit (0.8)"
                "\", "
                "multiValue: { used: 133169152, dead: 0, limit: 134217728}, "
                "attributeName: \"multiValueName\", subdb: \"ready\"}");
}

TEST_F("listener is updated when attribute stats change", Fixture)
{
   AttributeUsageStats stats;
   AddressSpaceUsage usage(AddressSpace(12, 10, 15), AddressSpace(22, 20, 25));
   stats.merge(usage, "my_attr", "my_subdb");
   f.setAttributeStats(stats);
   EXPECT_EQUAL(stats, f.listener->stats);
}

TEST_MAIN() { TEST_RUN_ALL(); }
