// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <map>
#include <ostream>
#include <string>
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
    constexpr BucketSpaceStats(size_t bucketsTotal_, size_t bucketsPending_) noexcept
        : _valid(true),
          _bucketsTotal(bucketsTotal_),
          _bucketsPending(bucketsPending_)
    {}
    constexpr BucketSpaceStats() noexcept
        : _valid(false),
          _bucketsTotal(0),
          _bucketsPending(0)
    {}

    static constexpr BucketSpaceStats make_invalid() noexcept { return {}; }

    [[nodiscard]] bool valid() const noexcept { return _valid; }
    size_t bucketsTotal() const noexcept { return _bucketsTotal; }
    size_t bucketsPending() const noexcept { return _bucketsPending; }

    bool operator==(const BucketSpaceStats& rhs) const noexcept {
        return (_valid == rhs._valid) &&
                (_bucketsTotal == rhs._bucketsTotal) &&
                (_bucketsPending == rhs._bucketsPending);
    }

    void merge(const BucketSpaceStats& rhs) noexcept {
        _valid = _valid && rhs._valid;
        _bucketsTotal += rhs._bucketsTotal;
        _bucketsPending += rhs._bucketsPending;
    }
};

std::ostream& operator<<(std::ostream& out, const BucketSpaceStats& stats);

/**
 * Interface that provides snapshots of bucket spaces statistics per content node.
 */
class BucketSpacesStatsProvider {
public:
    // Mapping from bucket space name to statistics for that bucket space.
    using BucketSpacesStats = std::map<std::string, BucketSpaceStats>;
    // Mapping from content node index to statistics for all bucket spaces on that node.
    using PerNodeBucketSpacesStats = std::unordered_map<uint16_t, BucketSpacesStats>;

    virtual ~BucketSpacesStatsProvider() = default;
    virtual PerNodeBucketSpacesStats getBucketSpacesStats() const = 0;
};

void merge_bucket_spaces_stats(BucketSpacesStatsProvider::BucketSpacesStats& dest,
                               const BucketSpacesStatsProvider::BucketSpacesStats& src);

void merge_per_node_bucket_spaces_stats(BucketSpacesStatsProvider::PerNodeBucketSpacesStats& dest,
                                        const BucketSpacesStatsProvider::PerNodeBucketSpacesStats& src);

}
