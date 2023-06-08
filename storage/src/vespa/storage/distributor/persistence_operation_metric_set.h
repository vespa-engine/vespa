// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/summetric.h>
#include <mutex>

namespace storage::api { class ReturnCode; }

namespace storage::distributor {

class PersistenceFailuresMetricSet : public metrics::MetricSet
{
public:
    explicit PersistenceFailuresMetricSet(metrics::MetricSet* owner);
    ~PersistenceFailuresMetricSet() override;

    metrics::SumMetric<metrics::LongCountMetric> sum;
    metrics::LongCountMetric notready;
    metrics::LongCountMetric notconnected;
    metrics::LongCountMetric wrongdistributor;
    metrics::LongCountMetric safe_time_not_reached;
    metrics::LongCountMetric storagefailure;
    metrics::LongCountMetric timeout;
    metrics::LongCountMetric busy;
    metrics::LongCountMetric inconsistent_bucket;
    metrics::LongCountMetric notfound;
    metrics::LongCountMetric concurrent_mutations;
    metrics::LongCountMetric test_and_set_failed;

    MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                      metrics::MetricSet* owner, bool includeUnused) const override;
};

class PersistenceOperationMetricSet : public metrics::MetricSet
{
    mutable std::mutex _mutex;
public:
    metrics::DoubleAverageMetric latency;
    metrics::LongCountMetric     ok;
    PersistenceFailuresMetricSet failures;

    PersistenceOperationMetricSet(const std::string& name, metrics::MetricSet* owner);
    explicit PersistenceOperationMetricSet(const std::string& name);
    ~PersistenceOperationMetricSet() override;

    MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                      metrics::MetricSet* owner, bool includeUnused) const override;

    /**
     * Increments appropriate success/failure count metrics based on the return
     * code provided in `result`.
     *
     * Does _not_ update latency metric.
     */
    void updateFromResult(const api::ReturnCode& result);

    class LockWrapper {
        std::unique_lock<std::mutex> _lock;
        PersistenceOperationMetricSet& _self;
    public:
        explicit LockWrapper(PersistenceOperationMetricSet& self)
            : _lock(self._mutex),
              _self(self)
        {}

        PersistenceOperationMetricSet* operator->() noexcept {
            return &_self;
        }
    };
    LockWrapper locked() noexcept {
        return LockWrapper(*this);
    }
};

}
