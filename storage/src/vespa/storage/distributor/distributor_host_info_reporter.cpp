// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_host_info_reporter.h"
#include "min_replica_provider.h"
#include "pendingmessagetracker.h"

#include <set>

using std::set;
using std::unordered_map;

namespace storage {
namespace distributor {

using Object = vespalib::JsonStream::Object;
using Array = vespalib::JsonStream::Array;
using End = vespalib::JsonStream::End;

DistributorHostInfoReporter::DistributorHostInfoReporter(
        LatencyStatisticsProvider& latencyProvider,
        MinReplicaProvider& minReplicaProvider)
    : _latencyProvider(latencyProvider),
      _minReplicaProvider(minReplicaProvider),
      _enabled(true)
{
}

namespace {

void
writeOperationStats(vespalib::JsonStream& stream,
                    const OperationStats& stats)
{
    stream << "put" << Object()
           << "latency-ms-sum" << stats.totalLatency.count()
           << "count" << stats.numRequests
           << End();
}

void
outputStorageNodes(vespalib::JsonStream& output,
                   const unordered_map<uint16_t, NodeStats>& nodeStats,
                   const unordered_map<uint16_t, uint32_t>& minReplica)
{
    set<uint16_t> nodes;
    for (auto& element : nodeStats) {
        nodes.insert(element.first);
    }
    for (auto& element : minReplica) {
        nodes.insert(element.first);
    }
    
    for (uint16_t node : nodes) {
        output << Object();
        {
            output << "node-index" << node;

            auto nodeStatsIt = nodeStats.find(node);
            if (nodeStatsIt != nodeStats.end()) {
                output << "ops-latency" << Object();
                {
                    writeOperationStats(output, nodeStatsIt->second.puts);
                }
                output << End();
            }

            auto minReplicaIt = minReplica.find(node);
            if (minReplicaIt != minReplica.end()) {
                output << "min-current-replication-factor"
                       << minReplicaIt->second;
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

    NodeStatsSnapshot nodeStats = _latencyProvider.getLatencyStatistics();
    std::unordered_map<uint16_t, uint32_t> minReplica =
        _minReplicaProvider.getMinReplica();

    output << "distributor" << Object();
    {
        output << "storage-nodes" << Array();

        outputStorageNodes(output, nodeStats.nodeToStats, minReplica);

        output << End();
    }
    output << End();
}

} // distributor
} // storage

