// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/loadmetric.hpp>
#include <vespa/metrics/metricmanager.h>
#include <vespa/metrics/metrics.h>
#include <vespa/metrics/summetric.hpp>
#include <vespa/vespalib/util/time.h>
#include <thread>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP(".metrics.test.stress");

namespace metrics {

namespace {
struct InnerMetricSet : public MetricSet {
    const LoadTypeSet& _loadTypes;
    LongCountMetric _count;
    LongAverageMetric _value1;
    LongAverageMetric _value2;
    SumMetric<LongAverageMetric> _valueSum;
    LoadMetric<LongAverageMetric> _load;

    InnerMetricSet(const char* name, const LoadTypeSet& lt, MetricSet* owner = 0);
    ~InnerMetricSet();

    MetricSet* clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                  MetricSet* owner, bool includeUnused) const override;
};

InnerMetricSet::InnerMetricSet(const char* name, const LoadTypeSet& lt, MetricSet* owner)
    : MetricSet(name, {}, "", owner),
      _loadTypes(lt),
      _count("count", {}, "", this),
      _value1("value1", {}, "", this),
      _value2("value2", {}, "", this),
      _valueSum("valuesum", {}, "", this),
      _load(lt, LongAverageMetric("load", {}, ""), this)
{
    _valueSum.addMetricToSum(_value1);
    _valueSum.addMetricToSum(_value2);
}
InnerMetricSet::~InnerMetricSet() = default;

    MetricSet*
    InnerMetricSet::clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                     MetricSet* owner, bool includeUnused) const
{
    if (copyType != CLONE) {
    return MetricSet::clone(ownerList, copyType, owner, includeUnused);
}
    InnerMetricSet * myset = new InnerMetricSet(getName().c_str(), _loadTypes, owner);
    myset->assignValues(*this);
    return myset;
}

struct OuterMetricSet : public MetricSet {
    InnerMetricSet _inner1;
    InnerMetricSet _inner2;
    SumMetric<InnerMetricSet> _innerSum;
    InnerMetricSet _tmp;
    LoadMetric<InnerMetricSet> _load;

    OuterMetricSet(const LoadTypeSet& lt, MetricSet* owner = 0);
    ~OuterMetricSet();
};

OuterMetricSet::OuterMetricSet(const LoadTypeSet& lt, MetricSet* owner)
        : MetricSet("outer", {}, "", owner),
          _inner1("inner1", lt, this),
          _inner2("inner2", lt, this),
          _innerSum("innersum", {}, "", this),
          _tmp("innertmp", lt, 0),
          _load(lt, _tmp, this)
{
    _innerSum.addMetricToSum(_inner1);
    _innerSum.addMetricToSum(_inner2);
}

OuterMetricSet::~OuterMetricSet() { }

struct Hammer : public document::Runnable {
    using UP = std::unique_ptr<Hammer>;

    OuterMetricSet& _metrics;
    const LoadTypeSet& _loadTypes;
    LoadType _nonexistingLoadType;

    Hammer(OuterMetricSet& metrics, const LoadTypeSet& lt,
           FastOS_ThreadPool& threadPool)
        : _metrics(metrics), _loadTypes(lt),
          _nonexistingLoadType(123, "nonexisting")
    {
        start(threadPool);
    }
    ~Hammer() {
        stop();
        join();
        //std::cerr << "Loadgiver thread joined\n";
    }

    void run() override {
        uint64_t i = 0;
        while (running()) {
            ++i;
            setMetrics(i, _metrics._inner1);
            setMetrics(i + 3, _metrics._inner2);
            const LoadType& loadType(_loadTypes[i % _loadTypes.size()]);
            setMetrics(i + 5, _metrics._load[loadType]);
        }
    }

    void setMetrics(uint64_t val, InnerMetricSet& set) {
        set._count.inc(val);
        set._value1.addValue(val);
        set._value2.addValue(val + 10);
        set._load[_loadTypes[val % _loadTypes.size()]].addValue(val);
    }
};

}


TEST(StressTest, test_stress)
{
    LoadTypeSet loadTypes;
    loadTypes.push_back(LoadType(0, "default"));
    loadTypes.push_back(LoadType(2, "foo"));
    loadTypes.push_back(LoadType(1, "bar"));

    OuterMetricSet metrics(loadTypes);

    LOG(info, "Starting load givers");
    FastOS_ThreadPool threadPool(256 * 1024);
    std::vector<Hammer::UP> hammers;
    for (uint32_t i=0; i<10; ++i) {
        hammers.push_back(std::make_unique<Hammer>(metrics, loadTypes, threadPool));
    }
    LOG(info, "Waiting to let loadgivers hammer a while");
    std::this_thread::sleep_for(5s);

    LOG(info, "Removing loadgivers");
    hammers.clear();

    LOG(info, "Printing end state");
    std::ostringstream ost;
    metrics.print(ost, true, "", 5);
    // std::cerr << ost.str() << "\n";
}

}
