// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/metrics/loadmetric.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/loadmetric.hpp>
#include <vespa/metrics/summetric.hpp>
#include <vespa/vespalib/gtest/gtest.h>

namespace metrics {

struct LoadTypeSetImpl : public LoadTypeSet {
    LoadTypeSetImpl() {
        push_back(LoadType(0, "default"));
    }
    LoadTypeSetImpl& add(uint32_t id, const char* name) {
        push_back(LoadType(id, name));
        return *this;
    }
    const LoadType& operator[](const std::string& name) const {
        for (uint32_t i=0; i<size(); ++i) {
            const LoadType& lt(LoadTypeSet::operator[](i));
            if (lt.getName() == name) return lt;
        }
        abort();
    }
};

TEST(LoadMetricTest, test_normal_usage)
{
    LoadTypeSetImpl loadTypes;
    loadTypes.add(32, "foo").add(1000, "bar");
    LoadMetric<LongValueMetric> metric(
            loadTypes, LongValueMetric("put", {}, "Put"));
}

namespace {
    struct MyMetricSet : public MetricSet {
        LongAverageMetric metric;

        MyMetricSet(MetricSet* owner = 0)
            : MetricSet("tick", {}, "", owner),
              metric("tack", {}, "", this)
        { }

        MetricSet* clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused = false) const override
        {
            if (copyType != CLONE) {
                return MetricSet::clone(ownerList, copyType, owner, includeUnused);
            }
            MyMetricSet * myset = new MyMetricSet(owner);
            myset->assignValues(*this);
            std::cerr << "org:" << this->toString(true) << std::endl;
            std::cerr << "clone:" << myset->toString(true) << std::endl;
            return myset;
        }
    };
}

void
test_clone(Metric::CopyType copyType)
{
    LoadTypeSetImpl loadTypes;
    loadTypes.add(32, "foo").add(1000, "bar");
    MetricSet top("top", {}, "");
    MyMetricSet myset;
    LoadMetric<MyMetricSet> metric(loadTypes, myset, &top);
    metric[loadTypes["foo"]].metric.addValue(5);

    std::vector<Metric::UP> ownerList;
    MetricSet::UP copy(dynamic_cast<MetricSet*>(top.clone(ownerList, copyType, 0, true)));
    ASSERT_TRUE(copy.get());

    std::string expected =
        "top:\n"
        "  tick:\n"
        "    sum:\n"
        "      tack average=5 last=5 min=5 max=5 count=1 total=5\n"
        "    default:\n"
        "      tack average=0 last=0 count=0 total=0\n"
        "    foo:\n"
        "      tack average=5 last=5 min=5 max=5 count=1 total=5\n"
        "    bar:\n"
        "      tack average=0 last=0 count=0 total=0";

    EXPECT_EQ(expected, std::string(top.toString(true)));
    EXPECT_EQ(expected, std::string(copy->toString(true)));
}

TEST(LoadMetricTest, test_inactive_copy)
{
    test_clone(Metric::INACTIVE);
}

TEST(LoadMetricTest, test_active_copy)
{
    test_clone(Metric::CLONE);
}

TEST(LoadMetricTest, test_adding)
{
    LoadTypeSetImpl loadTypes;
    loadTypes.add(32, "foo").add(1000, "bar");
    MetricSet top("top", {}, "");
    MyMetricSet myset;
    LoadMetric<MyMetricSet> metric(loadTypes, myset, &top);
    metric[loadTypes["foo"]].metric.addValue(5);

    std::vector<Metric::UP> ownerList;
    MetricSet::UP copy(dynamic_cast<MetricSet*>(
                top.clone(ownerList, Metric::INACTIVE, 0, false)));
    ASSERT_TRUE(copy.get());

    top.reset();

    top.addToSnapshot(*copy, ownerList);

    std::string expected =
        "top:\n"
        "  tick:\n"
        "    sum:\n"
        "      tack average=5 last=5 min=5 max=5 count=1 total=5\n"
        "    foo:\n"
        "      tack average=5 last=5 min=5 max=5 count=1 total=5";

    EXPECT_EQ(expected, std::string(copy->toString(true)));
}

}
