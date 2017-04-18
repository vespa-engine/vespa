// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/metrics/metrics.h>
#include <vespa/metrics/loadmetric.hpp>
#include <vespa/metrics/summetric.hpp>
#include <vespa/metrics/metricmanager.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace metrics {

struct SnapshotTest : public CppUnit::TestFixture {
    void testSnapshotTwoDays();

    CPPUNIT_TEST_SUITE(SnapshotTest);
    CPPUNIT_TEST(testSnapshotTwoDays);
    CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(SnapshotTest);

namespace {

struct SubSubMetricSet : public MetricSet {
    const LoadTypeSet& loadTypes;
    int incVal;
    LongCountMetric count1;
    LongCountMetric count2;
    LoadMetric<LongCountMetric> loadCount;
    SumMetric<LongCountMetric> countSum;
    DoubleValueMetric value1;
    DoubleValueMetric value2;
    LoadMetric<DoubleValueMetric> loadValue;
    SumMetric<DoubleValueMetric> valueSum;
    DoubleAverageMetric average1;
    DoubleAverageMetric average2;
    LoadMetric<DoubleAverageMetric> loadAverage;
    SumMetric<DoubleAverageMetric> averageSum;

    SubSubMetricSet(vespalib::stringref name, const LoadTypeSet& loadTypes_, MetricSet* owner = 0);
    ~SubSubMetricSet();
    virtual MetricSet* clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                             metrics::MetricSet* owner, bool includeUnused) const override;
    void incValues();
};

SubSubMetricSet::SubSubMetricSet(vespalib::stringref name, const LoadTypeSet& loadTypes_, MetricSet* owner)
    : MetricSet(name, "", "", owner),
      loadTypes(loadTypes_),
      incVal(1),
      count1("count1", "", "", this),
      count2("count2", "", "", this),
      loadCount(loadTypes, LongCountMetric("loadCount", "", ""), this),
      countSum("countSum", "", "", this),
      value1("value1", "", "", this),
      value2("value2", "", "", this),
      loadValue(loadTypes, DoubleValueMetric("loadValue", "", ""), this),
      valueSum("valueSum", "", "", this),
      average1("average1", "", "", this),
      average2("average2", "", "", this),
      loadAverage(loadTypes, DoubleAverageMetric("loadAverage", "", ""), this),
      averageSum("averageSum", "", "", this)
{
    countSum.addMetricToSum(count1);
    countSum.addMetricToSum(count2);
    valueSum.addMetricToSum(value1);
    valueSum.addMetricToSum(value2);
    averageSum.addMetricToSum(average1);
    averageSum.addMetricToSum(average2);
}
SubSubMetricSet::~SubSubMetricSet() { }

MetricSet*
SubSubMetricSet::clone(std::vector<Metric::UP> &ownerList,
                       CopyType copyType, metrics::MetricSet* owner,
                       bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (SubSubMetricSet*) (new SubSubMetricSet(
            getName(), loadTypes, owner))
            ->assignValues(*this);
}

void
SubSubMetricSet::incValues() {
    count1.inc(incVal);
    count2.inc(incVal);
    for (uint32_t i=0; i<loadTypes.size(); ++i) {
        loadCount[loadTypes[i]].inc(incVal);
    }
    value1.set(incVal);
    value2.set(incVal);
    for (uint32_t i=0; i<loadTypes.size(); ++i) {
        loadValue[loadTypes[i]].set(incVal);
    }
    average1.set(incVal);
    average2.set(incVal);
    for (uint32_t i=0; i<loadTypes.size(); ++i) {
        loadAverage[loadTypes[i]].set(incVal);
    }
}


struct SubMetricSet : public MetricSet {
    const LoadTypeSet& loadTypes;
    SubSubMetricSet set1;
    SubSubMetricSet set2;
    LoadMetric<SubSubMetricSet> loadSet;
    SumMetric<SubSubMetricSet> setSum;

    SubMetricSet(vespalib::stringref name, const LoadTypeSet& loadTypes_, MetricSet* owner = 0);
    ~SubMetricSet();

    MetricSet* clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                     metrics::MetricSet* owner, bool includeUnused) const override;

    void incValues();
};

SubMetricSet::SubMetricSet(vespalib::stringref name, const LoadTypeSet& loadTypes_, MetricSet* owner)
    : MetricSet(name, "", "", owner),
      loadTypes(loadTypes_),
      set1("set1", loadTypes, this),
      set2("set2", loadTypes, this),
      loadSet(loadTypes, *std::unique_ptr<SubSubMetricSet>(new SubSubMetricSet("loadSet", loadTypes)), this),
      setSum("setSum", "", "", this)
{
    setSum.addMetricToSum(set1);
    setSum.addMetricToSum(set2);
}
SubMetricSet::~SubMetricSet() { }

MetricSet*
SubMetricSet::clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                    metrics::MetricSet* owner, bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (SubMetricSet*) (new SubMetricSet(getName(), loadTypes, owner))
            ->assignValues(*this);
}

void
SubMetricSet::incValues() {
    set1.incValues();
    set2.incValues();
    for (uint32_t i=0; i<loadTypes.size(); ++i) {
        loadSet[loadTypes[i]].incValues();
    }
}

struct TestMetricSet : public MetricSet {
    const LoadTypeSet& loadTypes;
    SubMetricSet set1;
    SubMetricSet set2;
    LoadMetric<SubMetricSet> loadSet;
    SumMetric<SubMetricSet> setSum;

    TestMetricSet(vespalib::stringref name, const LoadTypeSet& loadTypes_, MetricSet* owner = 0);
    ~TestMetricSet();

    void incValues();
};


TestMetricSet::TestMetricSet(vespalib::stringref name, const LoadTypeSet& loadTypes_, MetricSet* owner)
    : MetricSet(name, "", "", owner),
      loadTypes(loadTypes_),
      set1("set1", loadTypes, this),
      set2("set2", loadTypes, this),
      loadSet(loadTypes, *std::unique_ptr<SubMetricSet>(new SubMetricSet("loadSet", loadTypes)), this),
      setSum("setSum", "", "", this)
{
    setSum.addMetricToSum(set1);
    setSum.addMetricToSum(set2);
}
TestMetricSet::~TestMetricSet() { }

void
TestMetricSet::incValues() {
    set1.incValues();
    set2.incValues();
    for (uint32_t i=0; i<loadTypes.size(); ++i) {
        loadSet[loadTypes[i]].incValues();
    }
}

struct FakeTimer : public MetricManager::Timer {
    uint32_t _timeInSecs;

    FakeTimer() : _timeInSecs(1) {}

    virtual time_t getTime() const override { return _timeInSecs; }
};

} // End of anonymous namespace

#define ASSERT_VALUE(value, snapshot, name) \
{ \
    const Metric* _metricValue_((snapshot).getMetrics().getMetric(name)); \
    if (_metricValue_ == 0) { \
        CPPUNIT_FAIL("Metric value '" + std::string(name) \
                     + "' not found in snapshot"); \
    } \
    CPPUNIT_ASSERT_EQUAL(value, \
                         int32_t(_metricValue_->getLongValue("value"))); \
}

void SnapshotTest::testSnapshotTwoDays()
{
        // Create load types
    LoadTypeSet loadTypes;
    loadTypes.push_back(LoadType(1, "foo"));
    loadTypes.push_back(LoadType(2, "bar"));

    TestMetricSet set("test", loadTypes);

    FakeTimer* timer;
    FastOS_ThreadPool threadPool(256 * 1024);
    MetricManager mm(
            std::unique_ptr<MetricManager::Timer>(timer = new FakeTimer));
    {
        MetricLockGuard lockGuard(mm.getMetricLock());
        mm.registerMetric(lockGuard, set);
    }
    mm.init("raw:consumer[1]\n"
            "consumer[0].name \"log\"", threadPool, false);
    mm.tick(mm.getMetricLock(), timer->_timeInSecs * 1000);

    for (uint32_t days=0; days<2; ++days) {
        for (uint32_t hour=0; hour<24; ++hour) {
            for (uint32_t fiveMin=0; fiveMin<12; ++fiveMin) {
                set.incValues();
                timer->_timeInSecs += 5 * 60;
                mm.tick(mm.getMetricLock(), timer->_timeInSecs * 1000);
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

    const MetricSnapshot* snap = 0;
        // active snapshot
    MetricLockGuard lockGuard(mm.getMetricLock());
    snap = &mm.getActiveMetrics(lockGuard);
    ASSERT_VALUE(0, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(0, *snap, "test.set1.set1.loadCount.foo");
    ASSERT_VALUE(0, *snap, "test.set1.set1.loadCount.sum");
    ASSERT_VALUE(0, *snap, "test.set1.set1.countSum");
    ASSERT_VALUE(0, *snap, "test.set1.loadSet.foo.count1");
    ASSERT_VALUE(0, *snap, "test.set1.loadSet.foo.countSum");
/* Current test procedure for fetching values, don't work in active sums of sets
    ASSERT_VALUE(0, *snap, "test.set1.loadSet.sum.count1");
    ASSERT_VALUE(0, *snap, "test.set1.loadSet.sum.loadCount.foo");
    ASSERT_VALUE(0, *snap, "test.set1.loadSet.sum.loadCount.sum");
    ASSERT_VALUE(0, *snap, "test.set1.loadSet.sum.countSum");
*/

        // 5 minute snapshot
    snap = &mm.getMetricSnapshot(lockGuard, 5 * 60);
    ASSERT_VALUE(1, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadCount.foo");
    ASSERT_VALUE(2, *snap, "test.set1.set1.loadCount.sum");
    ASSERT_VALUE(2, *snap, "test.set1.set1.countSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.count1");
    ASSERT_VALUE(2, *snap, "test.set1.loadSet.foo.countSum");
    ASSERT_VALUE(2, *snap, "test.set1.loadSet.sum.count1");
    ASSERT_VALUE(2, *snap, "test.set1.loadSet.sum.loadCount.foo");
    ASSERT_VALUE(4, *snap, "test.set1.loadSet.sum.loadCount.sum");
    ASSERT_VALUE(4, *snap, "test.set1.loadSet.sum.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.averageSum");

        // 1 hour snapshot
    snap = &mm.getMetricSnapshot(lockGuard, 60 * 60);
    ASSERT_VALUE(12, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(12, *snap, "test.set1.set1.loadCount.foo");
    ASSERT_VALUE(24, *snap, "test.set1.set1.loadCount.sum");
    ASSERT_VALUE(24, *snap, "test.set1.set1.countSum");
    ASSERT_VALUE(12, *snap, "test.set1.loadSet.foo.count1");
    ASSERT_VALUE(24, *snap, "test.set1.loadSet.foo.countSum");
    ASSERT_VALUE(24, *snap, "test.set1.loadSet.sum.count1");
    ASSERT_VALUE(24, *snap, "test.set1.loadSet.sum.loadCount.foo");
    ASSERT_VALUE(48, *snap, "test.set1.loadSet.sum.loadCount.sum");
    ASSERT_VALUE(48, *snap, "test.set1.loadSet.sum.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.averageSum");

        // 1 day snapshot
    snap = &mm.getMetricSnapshot(lockGuard, 24 * 60 * 60);
    ASSERT_VALUE(288, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(288, *snap, "test.set1.set1.loadCount.foo");
    ASSERT_VALUE(576, *snap, "test.set1.set1.loadCount.sum");
    ASSERT_VALUE(576, *snap, "test.set1.set1.countSum");
    ASSERT_VALUE(288, *snap, "test.set1.loadSet.foo.count1");
    ASSERT_VALUE(576, *snap, "test.set1.loadSet.foo.countSum");
    ASSERT_VALUE(576, *snap, "test.set1.loadSet.sum.count1");
    ASSERT_VALUE(576, *snap, "test.set1.loadSet.sum.loadCount.foo");
    ASSERT_VALUE(1152, *snap, "test.set1.loadSet.sum.loadCount.sum");
    ASSERT_VALUE(1152, *snap, "test.set1.loadSet.sum.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.averageSum");

        // total snapshot (2 days currently, not testing weeks)
    snap = &mm.getTotalMetricSnapshot(lockGuard);
    ASSERT_VALUE(576, *snap, "test.set1.set1.count1");
    ASSERT_VALUE(576, *snap, "test.set1.set1.loadCount.foo");
    ASSERT_VALUE(1152, *snap, "test.set1.set1.loadCount.sum");
    ASSERT_VALUE(1152, *snap, "test.set1.set1.countSum");
    ASSERT_VALUE(576, *snap, "test.set1.loadSet.foo.count1");
    ASSERT_VALUE(1152, *snap, "test.set1.loadSet.foo.countSum");
    ASSERT_VALUE(1152, *snap, "test.set1.loadSet.sum.count1");
    ASSERT_VALUE(1152, *snap, "test.set1.loadSet.sum.loadCount.foo");
    ASSERT_VALUE(2304, *snap, "test.set1.loadSet.sum.loadCount.sum");
    ASSERT_VALUE(2304, *snap, "test.set1.loadSet.sum.countSum");

    ASSERT_VALUE(1, *snap, "test.set1.set1.average1");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.set1.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.set1.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.foo.averageSum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.average1");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.foo");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.loadAverage.sum");
    ASSERT_VALUE(1, *snap, "test.set1.loadSet.sum.averageSum");
}

} // metrics
