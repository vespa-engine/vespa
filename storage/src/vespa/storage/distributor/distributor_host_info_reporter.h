// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/hostreporter/hostreporter.h>

namespace storage::distributor {

class BucketSpacesStatsProvider;
class MinReplicaProvider;

class DistributorHostInfoReporter : public HostReporter
{
public:
    DistributorHostInfoReporter(MinReplicaProvider& minReplicaProvider,
                                BucketSpacesStatsProvider& bucketSpacesStatsProvider);

    DistributorHostInfoReporter(const DistributorHostInfoReporter&) = delete;
    DistributorHostInfoReporter& operator=(const DistributorHostInfoReporter&) = delete;

    void report(vespalib::JsonStream& output) override;

private:
    MinReplicaProvider& _minReplicaProvider;
    BucketSpacesStatsProvider& _bucketSpacesStatsProvider;
};

} // storage::distributor
