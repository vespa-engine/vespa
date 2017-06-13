// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("attribute_usage_filter_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>

using proton::AttributeUsageFilter;
using proton::AttributeUsageStats;

namespace
{

search::AddressSpace enumStoreOverLoad(30 * 1024 * 1024 * UINT64_C(1024),
                                       0,
                                       32 * 1024 * 1024 * UINT64_C(1024));

search::AddressSpace multiValueOverLoad(127 * 1024 * 1024,
                                        0,
                                        128 * 1024 * 1024);



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

struct Fixture
{
    AttributeUsageFilter _filter;
    using State = AttributeUsageFilter::State;
    using Config = AttributeUsageFilter::Config;

    Fixture()
        : _filter()
    {
    }

    void testWrite(const vespalib::string &exp) {
        if (exp.empty()) {
            EXPECT_TRUE(_filter.acceptWriteOperation());
            State state = _filter.getAcceptState();
            EXPECT_TRUE(state.acceptWriteOperation());
            EXPECT_EQUAL(exp, state.message());
        } else {
            EXPECT_FALSE(_filter.acceptWriteOperation());
            State state = _filter.getAcceptState();
            EXPECT_FALSE(state.acceptWriteOperation());
            EXPECT_EQUAL(exp, state.message());
        }
    }

    void setAttributeStats(const AttributeUsageStats &stats) {
        _filter.setAttributeStats(stats);
    }
};

}

TEST_F("Check that default filter allows write", Fixture)
{
    f.testWrite("");
}


TEST_F("Check that enum store limit can be reached", Fixture)
{
    f._filter.setConfig(Fixture::Config(0.8, 1.0));
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
    f._filter.setConfig(Fixture::Config(1.0, 0.8));
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
    f._filter.setConfig(Fixture::Config(0.8, 0.8));
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

TEST_MAIN() { TEST_RUN_ALL(); }
