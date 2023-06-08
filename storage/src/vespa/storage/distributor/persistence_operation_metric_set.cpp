// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributormetricsset.h"
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/metrics/summetric.hpp>

namespace storage::distributor {

using metrics::MetricSet;

PersistenceFailuresMetricSet::PersistenceFailuresMetricSet(MetricSet* owner)
    : MetricSet("failures", {}, "Detailed failure statistics", owner),
      sum("total", {{"logdefault"},{"yamasdefault"}}, "Sum of all failures", this),
      notready("notready", {}, "The number of operations discarded because distributor was not ready", this),
      notconnected("notconnected", {}, "The number of operations discarded because there were no available storage nodes to send to", this),
      wrongdistributor("wrongdistributor", {}, "The number of operations discarded because they were sent to the wrong distributor", this),
      safe_time_not_reached("safe_time_not_reached", {},
                            "The number of operations that were transiently"
                            " failed due to them arriving before the safe "
                            "time point for bucket ownership handovers has "
                            "passed", this),
      storagefailure("storagefailure", {}, "The number of operations that failed in storage", this),
      timeout("timeout", {}, "The number of operations that failed because the operation timed out towards storage", this),
      busy("busy", {}, "The number of messages from storage that failed because the storage node was busy", this),
      inconsistent_bucket("inconsistent_bucket", {},
                          "The number of operations failed due to buckets "
                          "being in an inconsistent state or not found", this),
      notfound("notfound", {}, "The number of operations that failed because the document did not exist", this),
      concurrent_mutations("concurrent_mutations", {}, "The number of operations that were transiently failed due "
                           "to a mutating operation already being in progress for its document ID", this),
      test_and_set_failed("test_and_set_failed", {}, "The number of mutating operations that failed because "
                          "they specified a test-and-set condition that did not match the existing document", this)
{
    sum.addMetricToSum(notready);
    sum.addMetricToSum(notconnected);
    sum.addMetricToSum(wrongdistributor);
    sum.addMetricToSum(safe_time_not_reached);
    sum.addMetricToSum(storagefailure);
    sum.addMetricToSum(timeout);
    sum.addMetricToSum(busy);
    sum.addMetricToSum(inconsistent_bucket);
    // We don't consider the following as explicit failures (even though they're in the failure set)
    // and therefore don't count them as part of the aggregate sum:
    //
    //  - Test-and-set mismatches
    //  - Concurrent mutation failures
    //  - Document to be updated not found
    //
    // TODO introduce separate aggregate for such metrics. Presumably when we deprecate legacy metric paths.
}

PersistenceFailuresMetricSet::~PersistenceFailuresMetricSet() = default;

MetricSet *
PersistenceFailuresMetricSet::clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                                    MetricSet* owner, bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return dynamic_cast<PersistenceFailuresMetricSet*>(
            (new PersistenceFailuresMetricSet(owner))->assignValues(*this));
}

PersistenceOperationMetricSet::PersistenceOperationMetricSet(const std::string& name, MetricSet* owner)
    : MetricSet(name, {}, vespalib::make_string("Statistics for the %s command", name.c_str()), owner),
      latency("latency", {{"yamasdefault"}}, vespalib::make_string("The average latency of %s operations", name.c_str()), this),
      ok("ok", {{"logdefault"},{"yamasdefault"}}, vespalib::make_string("The number of successful %s operations performed", name.c_str()), this),
      failures(this)
{ }

PersistenceOperationMetricSet::PersistenceOperationMetricSet(const std::string& name)
    : PersistenceOperationMetricSet(name, nullptr)
{
}

PersistenceOperationMetricSet::~PersistenceOperationMetricSet() = default;

MetricSet *
PersistenceOperationMetricSet::clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                                     MetricSet* owner, bool includeUnused) const
{   
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return dynamic_cast<PersistenceOperationMetricSet*>(
            (new PersistenceOperationMetricSet(getName(), owner))->assignValues(*this));
}

void
PersistenceOperationMetricSet::updateFromResult(const api::ReturnCode& result)
{
    if (result.success()) {
        ok.inc();
    } else if (result.getResult() == api::ReturnCode::WRONG_DISTRIBUTION) {
        failures.wrongdistributor.inc();
    } else if (result.getResult() == api::ReturnCode::TIMEOUT) {
        failures.timeout.inc();
    } else if (result.getResult() == api::ReturnCode::TEST_AND_SET_CONDITION_FAILED) {
        failures.test_and_set_failed.inc();
    } else if (result.isBusy()) {
        failures.busy.inc();
    } else if (result.isBucketDisappearance()) {
        // Bucket not found/deleted codes imply that replicas are transiently
        // inconsistent in our DB or across replica nodes.
        failures.inconsistent_bucket.inc();
    } else if (result.isNodeDownOrNetwork()) {
        failures.notconnected.inc();
    } else {
        failures.storagefailure.inc();
    }
}

} // storage::distributor
