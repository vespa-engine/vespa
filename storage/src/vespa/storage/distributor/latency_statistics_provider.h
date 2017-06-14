// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>
#include <unordered_map>
#include <iosfwd>
#include <stdint.h>

namespace storage {
namespace distributor {

struct OperationStats {
    std::chrono::milliseconds totalLatency;
    uint64_t numRequests;

    OperationStats()
        : totalLatency(0), numRequests(0)
    {
    }
};

struct NodeStats {
    OperationStats puts;
};

std::ostream&
operator<<(std::ostream&, const OperationStats&);

std::ostream&
operator<<(std::ostream&, const NodeStats&);

struct NodeStatsSnapshot
{
    std::unordered_map<uint16_t, NodeStats> nodeToStats;
};

class LatencyStatisticsProvider
{
public:
    virtual ~LatencyStatisticsProvider() {}

    /**
     * Get a snapshot representation of the latency statistics towards a set of
     * nodes at the point of the call.
     *
     * Can be called at any time after registration from another thread context
     * and the call must thus be thread safe and data race free.
     */
    NodeStatsSnapshot getLatencyStatistics() const {
        return doGetLatencyStatistics();
    }

private:
    virtual NodeStatsSnapshot doGetLatencyStatistics() const = 0;
};

} // distributor
} // storage
