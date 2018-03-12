// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <unordered_map>

namespace storage::distributor {

/**
 * Statistics for a single bucket space on a content node.
 */
class BucketSpaceStats {
private:
    bool   _valid;
    size_t _bucketsTotal;
    size_t _bucketsPending;
public:
    BucketSpaceStats(size_t bucketsTotal_, size_t bucketsPending_)
        : _valid(true),
          _bucketsTotal(bucketsTotal_),
          _bucketsPending(bucketsPending_)
    {}
    BucketSpaceStats()
        : _valid(false),
          _bucketsTotal(0),
          _bucketsPending(0)
    {}

    static BucketSpaceStats make_invalid() noexcept { return {}; }

    bool valid() const noexcept { return _valid; }
    size_t bucketsTotal() const noexcept { return _bucketsTotal; }
    size_t bucketsPending() const noexcept { return _bucketsPending; }
};

/**
 * Interface that provides snapshots of bucket spaces statistics per content node.
 */
class BucketSpacesStatsProvider {
public:
    // Mapping from bucket space name to statistics for that bucket space.
    using BucketSpacesStats = std::map<vespalib::string, BucketSpaceStats>;
    // Mapping from content node index to statistics for all bucket spaces on that node.
    using PerNodeBucketSpacesStats = std::unordered_map<uint16_t, BucketSpacesStats>;

    virtual ~BucketSpacesStatsProvider() {}
    virtual PerNodeBucketSpacesStats getBucketSpacesStats() const = 0;
};

}
