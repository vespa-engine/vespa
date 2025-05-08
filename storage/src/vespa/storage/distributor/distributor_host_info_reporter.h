// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/hostreporter/hostreporter.h>
#include <vespa/storage/distributor/content_node_message_stats_tracker.h>
#include <mutex>

namespace storage::distributor {

class BucketSpacesStatsProvider;
class ContentNodeStatsProvider;
class MinReplicaProvider;

class DistributorHostInfoReporter : public HostReporter
{
public:
    DistributorHostInfoReporter(MinReplicaProvider& minReplicaProvider,
                                BucketSpacesStatsProvider& bucketSpacesStatsProvider,
                                ContentNodeStatsProvider& content_node_stats_provider);

    DistributorHostInfoReporter(const DistributorHostInfoReporter&) = delete;
    DistributorHostInfoReporter& operator=(const DistributorHostInfoReporter&) = delete;

    void report(vespalib::JsonStream& output) override;
    void on_periodic_callback(std::chrono::steady_clock::time_point steady_now) override;

private:
    [[nodiscard]] ContentNodeMessageStatsTracker::NodeStats thread_safe_node_stats_delta() const;

    MinReplicaProvider&                       _minReplicaProvider;
    BucketSpacesStatsProvider&                _bucketSpacesStatsProvider;
    ContentNodeStatsProvider&                 _content_node_stats_provider;
    ContentNodeMessageStatsTracker::NodeStats _prev_node_stats_full;
    ContentNodeMessageStatsTracker::NodeStats _node_stats_delta;
    std::chrono::steady_clock::time_point     _last_stat_sample_time;
    mutable std::mutex                        _stat_mutex;
};

} // storage::distributor
