// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/hostreporter/hostreporter.h>
#include <atomic>

namespace storage {
namespace distributor {

class BucketSpacesStatsProvider;
class MinReplicaProvider;
struct OperationStats;

class DistributorHostInfoReporter : public HostReporter
{
public:
    DistributorHostInfoReporter(MinReplicaProvider& minReplicaProvider,
                                BucketSpacesStatsProvider& bucketSpacesStatsProvider);

    DistributorHostInfoReporter(const DistributorHostInfoReporter&) = delete;
    DistributorHostInfoReporter& operator=(
            const DistributorHostInfoReporter&) = delete;

    void report(vespalib::JsonStream& output) override;

    /**
     * Set wether per-node latency, replication factors, merge stats etc are
     * to be included in the generated JSON report.
     *
     * Thread safe.
     */
    void enableReporting(bool enabled) noexcept {
        _enabled.store(enabled, std::memory_order_relaxed);
    }

    /**
     * Thread safe.
     */
    bool isReportingEnabled() const noexcept {
        return _enabled.load(std::memory_order_relaxed);
    }

private:
    MinReplicaProvider& _minReplicaProvider;
    BucketSpacesStatsProvider& _bucketSpacesStatsProvider;
    std::atomic<bool> _enabled;
};

} // distributor
} // storage

