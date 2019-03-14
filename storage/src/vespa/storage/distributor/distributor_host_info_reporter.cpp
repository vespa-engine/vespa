// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_spaces_stats_provider.h"
#include "distributor_host_info_reporter.h"
#include "min_replica_provider.h"
#include "pendingmessagetracker.h"

#include <set>

using std::set;
using std::unordered_map;

namespace storage {
namespace distributor {

using BucketSpacesStats = BucketSpacesStatsProvider::BucketSpacesStats;
using PerNodeBucketSpacesStats = BucketSpacesStatsProvider::PerNodeBucketSpacesStats;
using Object = vespalib::JsonStream::Object;
using Array = vespalib::JsonStream::Array;
using End = vespalib::JsonStream::End;

DistributorHostInfoReporter::DistributorHostInfoReporter(
        MinReplicaProvider& minReplicaProvider,
        BucketSpacesStatsProvider& bucketSpacesStatsProvider)
    : _minReplicaProvider(minReplicaProvider),
      _bucketSpacesStatsProvider(bucketSpacesStatsProvider),
      _enabled(true)
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

void
outputStorageNodes(vespalib::JsonStream& output,
                   const unordered_map<uint16_t, uint32_t>& minReplica,
                   const PerNodeBucketSpacesStats& bucketSpacesStats)
{
    set<uint16_t> nodes;
    for (const auto& element : minReplica) {
        nodes.insert(element.first);
    }
    for (const auto& element : bucketSpacesStats) {
        nodes.insert(element.first);
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
    if (!isReportingEnabled()) {
        return;
    }

    auto minReplica = _minReplicaProvider.getMinReplica();
    auto bucketSpacesStats = _bucketSpacesStatsProvider.getBucketSpacesStats();

    output << "distributor" << Object();
    {
        output << "storage-nodes" << Array();

        outputStorageNodes(output, minReplica, bucketSpacesStats);

        output << End();
    }
    output << End();
}

} // distributor
} // storage

