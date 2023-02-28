// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/metricmanager.h>
#include <vespa/metrics/metrics.h>
#include <vespa/metrics/summetric.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

namespace metrics {

namespace {

struct SubSubMetricSet : public MetricSet {
    int incVal;
    LongCountMetric count1;
    LongCountMetric count2;
    SumMetric<LongCountMetric> countSum;
    DoubleValueMetric value1;
    DoubleValueMetric value2;
    SumMetric<DoubleValueMetric> valueSum;
    DoubleAverageMetric average1;
    DoubleAverageMetric average2;
    SumMetric<DoubleAverageMetric> averageSum;

    SubSubMetricSet(vespalib::stringref name, MetricSet* owner = 0);
    ~SubSubMetricSet();
    MetricSet* clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                     metrics::MetricSet* owner, bool includeUnused) const override;
    void incValues();
};

SubSubMetricSet::SubSubMetricSet(vespalib::stringref name, MetricSet* owner)
    : MetricSet(name, {}, "", owner),
      incVal(1),
      count1("count1", {}, "", this),
      count2("count2", {}, "", this),
      countSum("countSum", {}, "", this),
      value1("value1", {}, "", this),
      value2("value2", {}, "", this),
      valueSum("valueSum", {}, "", this),
      average1("average1", {}, "", this),
      average2("average2", {}, "", this),
      averageSum("averageSum", {}, "", this)
{
    countSum.addMetricToSum(count1);
    countSum.addMetricToSum(count2);
    valueSum.addMetricToSum(value1);
    valueSum.addMetricToSum(value2);
    averageSum.addMetricToSum(average1);
    averageSum.addMetricToSum(average2);
}
SubSubMetricSet::~SubSubMetricSet() = default;

MetricSet*
SubSubMetricSet::clone(std::vector<Metric::UP> &ownerList,
                       CopyType copyType, metrics::MetricSet* owner,
                       bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (SubSubMetricSet*) (new SubSubMetricSet(
            getName(), owner))
            ->assignValues(*this);
}

void
SubSubMetricSet::incValues() {
    count1.inc(incVal);
    count2.inc(incVal);
    value1.set(incVal);
    value2.set(incVal);
    average1.set(incVal);
    average2.set(incVal);
}


struct SubMetricSet : public MetricSet {
    SubSubMetricSet set1;
    SubSubMetricSet set2;
    SumMetric<SubSubMetricSet> setSum;

    SubMetricSet(vespalib::stringref name, MetricSet* owner = 0);
    ~SubMetricSet();

    MetricSet* clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                     metrics::MetricSet* owner, bool includeUnused) const override;

    void incValues();
};

SubMetricSet::SubMetricSet(vespalib::stringref name, MetricSet* owner)
    : MetricSet(name, {}, "", owner),
      set1("set1", this),
      set2("set2", this),
      setSum("setSum", {}, "", this)
{
    setSum.addMetricToSum(set1);
    setSum.addMetricToSum(set2);
}
SubMetricSet::~SubMetricSet() = default;

MetricSet*
SubMetricSet::clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                    metrics::MetricSet* owner, bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (SubMetricSet*) (new SubMetricSet(getName(), owner))
            ->assignValues(*this);
}

void
SubMetricSet::incValues() {
    set1.incValues();
    set2.incValues();
}

struct TestMetricSet : public MetricSet {
    SubMetricSet set1;
    SubMetricSet set2;
    SumMetric<SubMetricSet> setSum;

    TestMetricSet(vespalib::stringref name);
    ~TestMetricSet();

    void incValues();
};


TestMetricSet::TestMetricSet(vespalib::stringref name)
    : MetricSet(name, {}, "", nullptr),
      set1("set1", this),
      set2("set2", this),
      setSum("setSum", {}, "", this)
{
    setSum.addMetricToSum(set1);
    setSum.addMetricToSum(set2);
}
TestMetricSet::~TestMetricSet() = default;

void
TestMetricSet::incValues() {
    set1.incValues();
    set2.incValues();
}

struct FakeTimer : public MetricManager::Timer {
    uint32_t _timeInSecs;
    FakeTimer() : _timeInSecs(1) {}
    time_point getTime() const override { return time_point(vespalib::from_s(_timeInSecs)); }
};

void ASSERT_VALUE(int32_t value, const MetricSnapshot & snapshot, const char *name) __attribute__((noinline));

void ASSERT_VALUE(int32_t value, const MetricSnapshot & snapshot, const char *name)
{
    const Metric* _metricValue_((snapshot).getMetrics().getMetric(name));
    if (_metricValue_ == 0) {
        FAIL() << ("Metric value '" + std::string(name) + "' not found in snapshot");
    }
    EXPECT_EQ(value, int32_t(_metricValue_->getLongValue("value")));
}

}

struct SnapshotTest : public ::testing::Test {
    time_t tick(MetricManager& mgr, time_t currentTime) {
        return mgr.tick(mgr.getMetricLock(), currentTime);
    }
};

TEST_F(SnapshotTest, test_snapshot_two_days)
{
    TestMetricSet set("test");

    FakeTimer* timer;
    MetricManager mm(std::unique_ptr<MetricManager::Timer>(timer = new FakeTimer));
    {
        MetricLockGuard lockGuard(mm.getMetricLock());
        mm.registerMetric(lockGuard, set);
    }
    mm.init(config::ConfigUri("raw:consumer[1]\n"
                              "consumer[0].name \"log\""),
            false);
    tick(mm, timer->_timeInSecs * 1000);

    for (uint32_t days=0; days<2; ++days) {
        for (uint32_t hour=0; hour<24; ++hour) {
            for (uint32_t fiveMin=0; fiveMin<12; ++fiveMin) {
                set.incValues();
                timer->_timeInSecs += 5 * 60;
                tick(mm, timer->_timeInSecs * 1000);
            }
        }
    }

    // Print all data. Useful for debugging. It's too much to verify
    // everything, so test will just verify some parts. Add more parts if you
    // find a failure.
    /*
    std::string regex = ".*";
    bool verbose = true;
    std::cout << "\n" << mm.getActiveMetrics().toString(mm, "", regex, verbose)
              << "\n\n";
    std::vector<uint32_t> periods(mm.getSnapshotPeriods());
    for (uint32_t i=0; i<periods.size(); ++i) {
        const MetricSnapshot& snap(mm.getMetricSnapshot(periods[i]));
        std::cout << snap.toString(mm, "", regex, verbose) << "\n\n";
    }

    std::cout << mm.getTotalMetricSnapshot().toString(mm, "", regex, verbose)
              << "\n";
    */

    // active snapshot
    MetricLockGuard lockGuard(mm.getMetricLock());
    const MetricSnapshot* snap = &mm.getActiveMetrics(lockGuard);
    ASSERT_VALUE(0, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(0, *snap, "test.set1.set1.countSum");

    // 5 minute snapshot
    snap = &mm.getMetricSnapshot(lockGuard, 5 * 60);
    ASSERT_VALUE(1, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(2, *snap, "test.set1.set1.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");

    // 1 hour snapshot
    snap = &mm.getMetricSnapshot(lockGuard, 60 * 60);
    ASSERT_VALUE(12, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(24, *snap, "test.set1.set1.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");

    // 1 day snapshot
    snap = &mm.getMetricSnapshot(lockGuard, 24 * 60 * 60);
    ASSERT_VALUE(288, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(576, *snap, "test.set1.set1.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");

    // total snapshot (2 days currently, not testing weeks)
    snap = &mm.getTotalMetricSnapshot(lockGuard);
    ASSERT_VALUE(576, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(1152, *snap, "test.set1.set1.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");
}

}
