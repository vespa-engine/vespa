// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

class PersistenceFailuresMetricSet : public metrics::MetricSet
{
public:
    PersistenceFailuresMetricSet(metrics::MetricSet* owner)
        : metrics::MetricSet("failures", "", "Detailed failure statistics", owner),
          sum("total", "logdefault yamasdefault", "Sum of all failures", this),
          notready("notready", "", "The number of operations discarded because distributor was not ready", this),
          notconnected("notconnected", "", "The number of operations discarded because there were no available storage nodes to send to", this),
          wrongdistributor("wrongdistributor", "", "The number of operations discarded because they were sent to the wrong distributor", this),
          safe_time_not_reached("safe_time_not_reached", "",
                                "The number of operations that were transiently"                                " failed due to them arriving before the safe "
                                "time point for bucket ownership handovers has "
                                "passed", this),
          storagefailure("storagefailure", "", "The number of operations that failed in storage", this),
          timeout("timeout", "", "The number of operations that failed because the operation timed out towards storage", this),
          busy("busy", "", "The number of messages from storage that failed because the storage node was busy", this),
          notfound("notfound", "", "The number of operations that failed because the document did not exist", this)
    {
        sum.addMetricToSum(notready);
        sum.addMetricToSum(notconnected);
        sum.addMetricToSum(wrongdistributor);
        sum.addMetricToSum(safe_time_not_reached);
        sum.addMetricToSum(storagefailure);
        sum.addMetricToSum(timeout);
        sum.addMetricToSum(busy);
        sum.addMetricToSum(notfound);
    }

    metrics::SumMetric<metrics::LongCountMetric> sum;
    metrics::LongCountMetric notready;
    metrics::LongCountMetric notconnected;
    metrics::LongCountMetric wrongdistributor;
    metrics::LongCountMetric safe_time_not_reached;
    metrics::LongCountMetric storagefailure;
    metrics::LongCountMetric timeout;
    metrics::LongCountMetric busy;
    metrics::LongCountMetric notfound;

    virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                          CopyType copyType,
                          metrics::MetricSet* owner,
                          bool includeUnused) const
    {
        if (copyType == INACTIVE) {
            return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
        }
        return (PersistenceFailuresMetricSet*)
                (new PersistenceFailuresMetricSet(owner))->assignValues(*this);
    }
    PersistenceFailuresMetricSet* operator&() { return this; }
};

class PersistenceOperationMetricSet : public metrics::MetricSet
{
public:
    metrics::DoubleAverageMetric latency;
    metrics::LongCountMetric ok;
    PersistenceFailuresMetricSet failures;

    PersistenceOperationMetricSet(const std::string& name,
                                   metrics::MetricSet* owner = 0)
        : metrics::MetricSet(name,
                             "",
                             vespalib::make_string("Statistics for the %s command",
                                     name.c_str()),
                             owner,
                             "operationtype"),
          latency("latency",
                  "yamasdefault",
                  vespalib::make_string("The average latency of %s operations",
                                        name.c_str()),
                  this),
          ok("ok",
             "logdefault yamasdefault",
             vespalib::make_string("The number of successful %s operations performed",
                                   name.c_str()),
             this),
          failures(this)
    {
    }

    virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                          CopyType copyType,
                          metrics::MetricSet* owner,
                          bool includeUnused) const
    {
        if (copyType == INACTIVE) {
            return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
        }
        return (PersistenceOperationMetricSet*)
                (new PersistenceOperationMetricSet(getName(), owner))
                    ->assignValues(*this);
    }
    PersistenceOperationMetricSet* operator&() { return this; }


};

class DistributorMetricSet : public metrics::MetricSet
{
public:
    metrics::LoadMetric<PersistenceOperationMetricSet> puts;
    metrics::LoadMetric<PersistenceOperationMetricSet> updates;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_puts;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> removes;
    metrics::LoadMetric<PersistenceOperationMetricSet> removelocations;
    metrics::LoadMetric<PersistenceOperationMetricSet> gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> stats;
    metrics::LoadMetric<PersistenceOperationMetricSet> multioperations;
    metrics::DoubleAverageMetric recoveryModeTime;
    metrics::LongValueMetric docsStored;
    metrics::LongValueMetric bytesStored;

    DistributorMetricSet(const metrics::LoadTypeSet& lt)
        : metrics::MetricSet("distributor", "distributor", ""),
          puts(lt, *&PersistenceOperationMetricSet("puts"), this),
          updates(lt, *&PersistenceOperationMetricSet("updates"), this),
          update_puts(lt, *&PersistenceOperationMetricSet("update_puts"), this),
          update_gets(lt, *&PersistenceOperationMetricSet("update_gets"), this),
          removes(lt, *&PersistenceOperationMetricSet("removes"), this),
          removelocations(lt, *&PersistenceOperationMetricSet("removelocations"), this),
          gets(lt, *&PersistenceOperationMetricSet("gets"), this),
          stats(lt, *&PersistenceOperationMetricSet("stats"), this),
          multioperations(lt, *&PersistenceOperationMetricSet("multioperations"), this),
          recoveryModeTime("recoverymodeschedulingtime", "",
                           "Time spent scheduling operations in recovery mode "
                           "after receiving new cluster state", this),
          docsStored("docsstored", "logdefault yamasdefault",
                     "Number of documents stored in all buckets controlled by "
                     "this distributor", this),
          bytesStored("bytesstored", "logdefault yamasdefault",
                      "Number of bytes stored in all buckets controlled by "
                      "this distributor", this)
        {
            docsStored.logOnlyIfSet();
            bytesStored.logOnlyIfSet();
        }

};

}

