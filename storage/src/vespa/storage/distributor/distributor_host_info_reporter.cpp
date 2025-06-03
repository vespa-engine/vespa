// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_spaces_stats_provider.h"
#include "content_node_stats_provider.h"
#include "distributor_host_info_reporter.h"
#include "min_replica_provider.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <chrono>
#include <set>

namespace storage::distributor {

using BucketSpacesStats = BucketSpacesStatsProvider::BucketSpacesStats;
using PerNodeBucketSpacesStats = BucketSpacesStatsProvider::PerNodeBucketSpacesStats;
using Object = vespalib::JsonStream::Object;
using Array = vespalib::JsonStream::Array;
using End = vespalib::JsonStream::End;

namespace {

// We report back response error statistics to the cluster controller for non-overlapping
// windows of time. Hardcode this window to 60 seconds for now, since there doesn't seem
// to be much real value in having this configurable.
constexpr std::chrono::duration content_node_stats_sample_window = 60s;

}

DistributorHostInfoReporter::DistributorHostInfoReporter(
        MinReplicaProvider& minReplicaProvider,
        BucketSpacesStatsProvider& bucketSpacesStatsProvider,
        ContentNodeStatsProvider& content_node_stats_provider)
    : _minReplicaProvider(minReplicaProvider),
      _bucketSpacesStatsProvider(bucketSpacesStatsProvider),
      _content_node_stats_provider(content_node_stats_provider),
      _prev_node_stats_full(),
      _node_stats_delta(),
      _last_stat_sample_time(),
      _stat_mutex()
{
}

namespace {

void
writeBucketSpacesStats(vespalib::JsonStream& stream,
                       const BucketSpacesStats& stats)
{
    for (const auto& elem : stats) {
        stream << Object() << "name" << elem.first;
        if (elem.second.valid()) {
            stream << "buckets" << Object()
                    << "total" << elem.second.bucketsTotal()
                    << "pending" << elem.second.bucketsPending()
                    << End();
        }
        stream << End();
    }
}

void write_single_error_stat_if_nonzero(vespalib::JsonStream& stream, std::string_view err_name, uint64_t err_counter) {
    if (err_counter == 0) {
        return;
    }
    stream << err_name << err_counter;
}

void write_content_node_stats(vespalib::JsonStream& stream, const ContentNodeMessageStats& stats) {
    stream << "response-stats" << Object();
    constexpr double delta_s = std::chrono::duration<double>(content_node_stats_sample_window).count();
    stream << "sample-window-sec" << delta_s // allows rate to be computed. TODO don't include?
           << "total-count"       << stats.sum_received(); // allows ratio to be computed per error category
    stream << "errors" << Object();
    write_single_error_stat_if_nonzero(stream, "network",       stats.recv_network_error);
    write_single_error_stat_if_nonzero(stream, "clock-skew",    stats.recv_clock_skew_error);
    // TODO consider if it gives value to include this field, since it's not directly actionable.
    write_single_error_stat_if_nonzero(stream, "uncategorized", stats.recv_other_error);
    stream << End() << End();
}

[[nodiscard]] bool should_include_stats(const ContentNodeMessageStats& stats) noexcept {
    // TODO consider an error rate (ratio?) threshold for inclusion
    // TODO look at all errors. For now, only care about including the entry if
    //  there's at least one network-related error. Can trivially relax this later
    //  once the cluster controller starts looking at other data points.
    return stats.recv_network_error > 0;
}

void
outputStorageNodes(vespalib::JsonStream &output,
                   const MinReplicaMap& minReplica,
                   const PerNodeBucketSpacesStats& bucketSpacesStats,
                   const ContentNodeMessageStatsTracker::NodeStats& node_stats)
{
    std::set<uint16_t> nodes;
    for (const auto& element : minReplica) {
        nodes.insert(element.first);
    }
    for (const auto& element : bucketSpacesStats) {
        nodes.insert(element.first);
    }
    for (const auto& element : node_stats.per_node) {
        if (should_include_stats(element.second)) {
            nodes.insert(element.first);
        }
    }
    
    for (uint16_t node : nodes) {
        output << Object();
        {
            output << "node-index" << node;

            auto minReplicaIt = minReplica.find(node);
            if (minReplicaIt != minReplica.end()) {
                output << "min-current-replication-factor"
                       << minReplicaIt->second;
            }

            auto stats_it = node_stats.per_node.find(node);
            if (stats_it != node_stats.per_node.end() && should_include_stats(stats_it->second)) {
                write_content_node_stats(output, stats_it->second);
            }

            auto bucketSpacesStatsIt = bucketSpacesStats.find(node);
            if (bucketSpacesStatsIt != bucketSpacesStats.end()) {
                output << "bucket-spaces" << Array();
                writeBucketSpacesStats(output, bucketSpacesStatsIt->second);
                output << End();
            }
        }
        output << End();
    }
}

}  // anonymous namespace

void
DistributorHostInfoReporter::report(vespalib::JsonStream& output)
{
    auto minReplica = _minReplicaProvider.getMinReplica();
    auto bucketSpacesStats = _bucketSpacesStatsProvider.per_node_bucket_spaces_stats();
    auto global_stats = _bucketSpacesStatsProvider.distributor_global_stats();
    auto node_stats = thread_safe_node_stats_delta();

    output << "distributor" << Object();
    if (global_stats.valid()) {
        output << "global-stats" << Object()
               << "stored-document-count" << global_stats.documents_total()
               << "stored-document-bytes" << global_stats.bytes_total()
               << End();
    }
    {
        output << "storage-nodes" << Array();
        outputStorageNodes(output, minReplica, bucketSpacesStats, node_stats);
        output << End();
    }
    output << End();
}

ContentNodeMessageStatsTracker::NodeStats
DistributorHostInfoReporter::thread_safe_node_stats_delta() const
{
    std::lock_guard lock(_stat_mutex);
    return _node_stats_delta;
}

void
DistributorHostInfoReporter::on_periodic_callback(std::chrono::steady_clock::time_point steady_now)
{
    std::lock_guard lock(_stat_mutex);
    if (steady_now - _last_stat_sample_time >= content_node_stats_sample_window) {
        auto stats_now = _content_node_stats_provider.content_node_stats();
        _node_stats_delta = stats_now.sparse_subtracted(_prev_node_stats_full);
        _prev_node_stats_full = std::move(stats_now);
        _last_stat_sample_time = steady_now;
    }
}

}

