// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/metricmanager.h>
#include <vespa/metrics/metrics.h>
#include <vespa/metrics/summetric.hpp>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP(".metrics.test.stress");

namespace metrics {

namespace {
struct InnerMetricSet : public MetricSet {
    LongCountMetric _count;
    LongAverageMetric _value1;
    LongAverageMetric _value2;
    SumMetric<LongAverageMetric> _valueSum;

    InnerMetricSet(const char* name, MetricSet* owner = 0);
    ~InnerMetricSet();

    MetricSet* clone(std::vector<Metric::UP> &ownerList, CopyType copyType,
                  MetricSet* owner, bool includeUnused) const override;
};

InnerMetricSet::InnerMetricSet(const char* name, MetricSet* owner)
    : MetricSet(name, {}, "", owner),
      _count("count", {}, "", this),
      _value1("value1", {}, "", this),
      _value2("value2", {}, "", this),
      _valueSum("valuesum", {}, "", this)
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
    InnerMetricSet * myset = new InnerMetricSet(getName().c_str(), owner);
    myset->assignValues(*this);
    return myset;
}

struct OuterMetricSet : public MetricSet {
    InnerMetricSet _inner1;
    InnerMetricSet _inner2;
    SumMetric<InnerMetricSet> _innerSum;
    InnerMetricSet _tmp;

    OuterMetricSet(MetricSet* owner = 0);
    ~OuterMetricSet();
};

OuterMetricSet::OuterMetricSet(MetricSet* owner)
        : MetricSet("outer", {}, "", owner),
          _inner1("inner1", this),
          _inner2("inner2", this),
          _innerSum("innersum", {}, "", this),
          _tmp("innertmp", 0)
{
    _innerSum.addMetricToSum(_inner1);
    _innerSum.addMetricToSum(_inner2);
}

OuterMetricSet::~OuterMetricSet() = default;

struct Hammer : public document::Runnable {
    using UP = std::unique_ptr<Hammer>;

    OuterMetricSet& _metrics;

    Hammer(OuterMetricSet& metrics,FastOS_ThreadPool& threadPool)
        : _metrics(metrics)
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
        }
    }

    void setMetrics(uint64_t val, InnerMetricSet& set) {
        set._count.inc(val);
        set._value1.addValue(val);
        set._value2.addValue(val + 10);
    }
};

}


TEST(StressTest, test_stress)
{
    OuterMetricSet metrics;

    LOG(info, "Starting load givers");
    FastOS_ThreadPool threadPool(256_Ki);
    std::vector<Hammer::UP> hammers;
    for (uint32_t i=0; i<10; ++i) {
        hammers.push_back(std::make_unique<Hammer>(metrics, threadPool));
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
