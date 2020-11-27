// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributormetricsset.h"
#include <vespa/vespalib/util/memoryusage.h>

namespace storage::distributor {

using metrics::MetricSet;

BucketDbMetrics::BucketDbMetrics(const vespalib::string& db_type, metrics::MetricSet* owner)
    : metrics::MetricSet("bucket_db", {{"bucket_db_type", db_type}}, "", owner),
      memory_usage(this)
{}

BucketDbMetrics::~BucketDbMetrics() = default;

//TODO Vespa 8 all metrics with .sum in the name should have that removed.
DistributorMetricSet::DistributorMetricSet()
    : MetricSet("distributor", {{"distributor"}}, ""),
      puts("puts.sum", this),
      updates(this),
      update_puts("update_puts", this),
      update_gets("update_gets", this),
      update_metadata_gets("update_metadata_gets", this),
      removes("removes.sum", this),
      removelocations("removelocations.sum", this),
      gets("gets.sum", this),
      stats("stats", this),
      getbucketlists("getbucketlists", this),
      visits(this),
      stateTransitionTime("state_transition_time", {},
              "Time it takes to complete a cluster state transition. If a "
              "state transition is preempted before completing, its elapsed "
              "time is counted as part of the total time spent for the final, "
              "completed state transition", this),
      set_cluster_state_processing_time("set_cluster_state_processing_time", {},
              "Elapsed time where the distributor thread is blocked on processing "
              "its bucket database upon receiving a new cluster state", this),
      activate_cluster_state_processing_time("activate_cluster_state_processing_time", {},
              "Elapsed time where the distributor thread is blocked on merging pending "
              "bucket info into its bucket database upon activating a cluster state", this),
      recoveryModeTime("recoverymodeschedulingtime", {},
              "Time spent scheduling operations in recovery mode "
              "after receiving new cluster state", this),
      docsStored("docsstored",
              {{"logdefault"},{"yamasdefault"}},
              "Number of documents stored in all buckets controlled by "
              "this distributor", this),
      bytesStored("bytesstored",
              {{"logdefault"},{"yamasdefault"}},
              "Number of bytes stored in all buckets controlled by "
              "this distributor", this),
      mutable_dbs("mutable", this),
      read_only_dbs("read_only", this)
{}

DistributorMetricSet::~DistributorMetricSet() = default;

} // storage
