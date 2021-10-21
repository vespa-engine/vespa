// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "idealstatemetricsset.h"
namespace storage {

namespace distributor {

OperationMetricSet::OperationMetricSet(const std::string& name, metrics::Metric::Tags tags, const std::string& description, MetricSet* owner)
    : MetricSet(name, std::move(tags), description, owner),
      pending("pending",
              {{"logdefault"},{"yamasdefault"}},
              "The number of operations pending", this),
      ok("done_ok",
         {{"logdefault"},{"yamasdefault"}},
         "The number of operations successfully performed", this),
      failed("done_failed",
             {{"logdefault"},{"yamasdefault"}},
             "The number of operations that failed", this),
      blocked("blocked",
              {{"logdefault"},{"yamasdefault"}},
              "The number of operations blocked by blocking operation starter", this),
      throttled("throttled",
                {{"logdefault"},{"yamasdefault"}},
                "The number of operations throttled by throttling operation starter", this)
{}

OperationMetricSet::~OperationMetricSet() = default;

GcMetricSet::GcMetricSet(const std::string& name, metrics::Metric::Tags tags, const std::string& description, MetricSet* owner)
        : OperationMetricSet(name, std::move(tags), description, owner),
          documents_removed("documents_removed",
                           {{"logdefault"},{"yamasdefault"}},
                           "Number of documents removed by GC operations", this)
{}

GcMetricSet::~GcMetricSet() = default;

MergeBucketMetricSet::MergeBucketMetricSet(const std::string& name, metrics::Metric::Tags tags, const std::string& description, MetricSet* owner)
    : OperationMetricSet(name, std::move(tags), description, owner),
      source_only_copy_changed("source_only_copy_changed",
                               {{"logdefault"},{"yamasdefault"}},
                               "The number of merge operations where source-only copy changed", this),
      source_only_copy_delete_blocked("source_only_copy_delete_blocked",
                                      {{"logdefault"},{"yamasdefault"}},
                                      "The number of merge operations where delete of unchanged source-only copies was blocked", this),
      source_only_copy_delete_failed("source_only_copy_delete_failed",
                                      {{"logdefault"},{"yamasdefault"}},
                                      "The number of merge operations where delete of unchanged source-only copies failed", this)
{
}

MergeBucketMetricSet::~MergeBucketMetricSet() = default;

void
IdealStateMetricSet::createOperationMetrics() {
    typedef IdealStateOperation ISO;
    operations.resize(ISO::OPERATION_COUNT);
    // Note: naked new is used instead of make_shared due to the latter not being
    // able to properly transitively deduce the types for the tag initializer lists.
    operations[ISO::DELETE_BUCKET] = std::shared_ptr<OperationMetricSet>(
            new OperationMetricSet("delete_bucket",
                                   {{"logdefault"},{"yamasdefault"}},
                                   "Operations to delete excess buckets on storage nodes", this));
    operations[ISO::MERGE_BUCKET] = std::make_shared<MergeBucketMetricSet>
                                    ("merge_bucket",
                                     metrics::Metric::Tags{{"logdefault"},{"yamasdefault"}},
                                     "Operations to merge buckets that are out of sync", this);
    operations[ISO::SPLIT_BUCKET] = std::shared_ptr<OperationMetricSet>(
            new OperationMetricSet("split_bucket",
                                   {{"logdefault"},{"yamasdefault"}},
                                   "Operations to split buckets that are larger than the configured size", this));
    operations[ISO::JOIN_BUCKET] = std::shared_ptr<OperationMetricSet>(
            new OperationMetricSet("join_bucket",
                                   {{"logdefault"},{"yamasdefault"}},
                                   "Operations to join buckets that in sum are smaller than the configured size", this));
    operations[ISO::SET_BUCKET_STATE] = std::shared_ptr<OperationMetricSet>(
            new OperationMetricSet("set_bucket_state",
                                   {{"logdefault"},{"yamasdefault"}},
                                   "Operations to set active/ready state for bucket copies", this));
    operations[ISO::GARBAGE_COLLECTION] = std::shared_ptr<OperationMetricSet>(
            new GcMetricSet("garbage_collection",
                            {{"logdefault"},{"yamasdefault"}},
                            "Operations to garbage collect data from buckets", this));
}

IdealStateMetricSet::IdealStateMetricSet()
    : MetricSet("idealstate", {{"idealstate"}}, "Statistics for ideal state generation"),
      idealstate_diff("idealstate_diff",
            {{"logdefault"},{"yamasdefault"}},
            "A number representing the current difference from the ideal "
            "state. This is a number that decreases steadily as the system "
            "is getting closer to the ideal state", this),
      buckets_toofewcopies("buckets_toofewcopies",
            {{"logdefault"},{"yamasdefault"}},
            "The number of buckets the distributor controls that have less "
            "than the desired redundancy", this),
      buckets_toomanycopies("buckets_toomanycopies",
            {{"logdefault"},{"yamasdefault"}},
            "The number of buckets the distributor controls that have more "
            "than the desired redundancy", this),
      buckets("buckets",
            {{"logdefault"},{"yamasdefault"}},
            "The number of buckets the distributor controls", this),
      buckets_notrusted("buckets_notrusted",
            {{"logdefault"},{"yamasdefault"}},
            "The number of buckets that have no trusted copies.", this),
      buckets_rechecking("buckets_rechecking",
            {{"logdefault"},{"yamasdefault"}},
            "The number of buckets that we are rechecking for "
            "ideal state operations", this),
      buckets_replicas_moving_out("bucket_replicas_moving_out",
            {{"logdefault"},{"yamasdefault"}},
            "Bucket replicas that should be moved out, e.g. retirement case or node "
            "added to cluster that has higher ideal state priority.", this),
      buckets_replicas_copying_in("bucket_replicas_copying_in",
            {{"logdefault"},{"yamasdefault"}},
            "Bucket replicas that should be copied in, e.g. node does not have a "
            "replica for a bucket that it is in ideal state for", this),
      buckets_replicas_copying_out("bucket_replicas_copying_out",
            {{"logdefault"},{"yamasdefault"}},
            "Bucket replicas that should be copied out, e.g. node is in ideal state "
            "but might have to provide data other nodes in a merge", this),
      buckets_replicas_syncing("bucket_replicas_syncing",
            {{"logdefault"},{"yamasdefault"}},
            "Bucket replicas that need syncing due to mismatching metadata", this),
      max_observed_time_since_last_gc_sec("max_observed_time_since_last_gc_sec",
            {{"logdefault"},{"yamasdefault"}},
            "Maximum time (in seconds) since GC was last successfully run for a bucket. "
            "Aggregated max value across all buckets on the distributor.", this),
      nodesPerMerge("nodes_per_merge", {}, "The number of nodes involved in a single merge operation.", this)
{
    createOperationMetrics();
}

IdealStateMetricSet::~IdealStateMetricSet() = default;

void IdealStateMetricSet::setPendingOperations(const std::vector<uint64_t>& newMetrics) {
    for (uint32_t i = 0; i < IdealStateOperation::OPERATION_COUNT; i++) {
        operations[i]->pending.set(newMetrics[i]);
    }

    idealstate_diff.set(
        operations[IdealStateOperation::DELETE_BUCKET]->pending.getLast() +
        operations[IdealStateOperation::MERGE_BUCKET]->pending.getLast() * 10 +
        operations[IdealStateOperation::SPLIT_BUCKET]->pending.getLast() * 4 +
        operations[IdealStateOperation::JOIN_BUCKET]->pending.getLast() * 2 +
        operations[IdealStateOperation::SET_BUCKET_STATE]->pending.getLast());
}

}

}

