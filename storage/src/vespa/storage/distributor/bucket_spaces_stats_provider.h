// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <map>
#include <ostream>
#include <string>
#include <unordered_map>

namespace storage::distributor {

/**
 * Distributor-global statistics across bucket spaces and content nodes.
 */
class DistributorGlobalStats {
    bool     _valid;
    uint64_t _documents_total;
    uint64_t _bytes_total;
public:
    constexpr DistributorGlobalStats() noexcept
        : _valid(false),
          _documents_total(0),
          _bytes_total(0)
    {}
    constexpr DistributorGlobalStats(uint64_t documents_total, uint64_t bytes_total) noexcept
        : _valid(true),
          _documents_total(documents_total),
          _bytes_total(bytes_total)
    {}

    [[nodiscard]] constexpr static DistributorGlobalStats make_invalid() noexcept { return {}; }
    [[nodiscard]] constexpr static DistributorGlobalStats make_empty_but_valid() noexcept { return {0, 0}; }

    [[nodiscard]] bool valid() const noexcept { return _valid; }
    [[nodiscard]] uint64_t documents_total() const noexcept { return _documents_total; }
    [[nodiscard]] uint64_t bytes_total() const noexcept { return _bytes_total; }
    bool operator==(const DistributorGlobalStats&) const noexcept = default;
    void merge(const DistributorGlobalStats& rhs) noexcept {
        _valid = _valid && rhs._valid;
        _documents_total += rhs._documents_total;
        _bytes_total += rhs._bytes_total;
    }
};

/**
 * Statistics for a single bucket space on a content node.
 */
class BucketSpaceStats {
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

    bool operator==(const BucketSpaceStats& rhs) const noexcept;

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
    [[nodiscard]] virtual PerNodeBucketSpacesStats per_node_bucket_spaces_stats() const = 0;
    [[nodiscard]] virtual DistributorGlobalStats distributor_global_stats() const = 0;
};

void merge_bucket_spaces_stats(BucketSpacesStatsProvider::BucketSpacesStats& dest,
                               const BucketSpacesStatsProvider::BucketSpacesStats& src);

void merge_per_node_bucket_spaces_stats(BucketSpacesStatsProvider::PerNodeBucketSpacesStats& dest,
                                        const BucketSpacesStatsProvider::PerNodeBucketSpacesStats& src);

}
