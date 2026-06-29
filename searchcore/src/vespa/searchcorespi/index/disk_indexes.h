// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <memory>
#include <mutex>
#include <string>

namespace searchcorespi::common {
class ResourceUsage;
}

namespace searchcorespi::index {

class IndexDiskDir;
class IndexDiskDirState;
class IndexDiskLayout;

/**
 * Class used to keep track of the set of disk indexes in an index maintainer.
 * The index directories are used as identifiers.
 *
 * DiskIndexCleaner will remove old disk indexes not marked active,
 * i.e. old disk indexes used by old index collections are not removed.
 *
 * At start of fusion, an entry for fusion output index is added, to allow for
 * tracking of transient disk use while fusion is ongoing. If fusion fails then
 * the entry is removed, otherwise the entry is marked active as a side effect
 * of setting up a new index collection.
 */
class DiskIndexes {
    std::map<IndexDiskDir, IndexDiskDirState> _active;
    uint64_t                                  _sum_size_on_disk;
    uint64_t                                  _sum_stale_size_on_disk;
    std::chrono::steady_clock::duration       _sum_flush_duration;
    std::chrono::steady_clock::duration       _last_fusion_flush_duration;
    std::chrono::steady_clock::duration       _last_flush_duration;
    mutable std::mutex                        _lock;

    void remove_from_sum(const IndexDiskDirState& state);

public:
    class FusionStats {
        uint64_t                            _estimated_size_on_disk;
        std::chrono::steady_clock::duration _last_flush_duration;
        std::chrono::steady_clock::duration _estimated_flush_duration;

    public:
        FusionStats(uint64_t estimated_size_on_disk_, std::chrono::steady_clock::duration last_flush_duration_,
                    std::chrono::steady_clock::duration estimated_flush_duration_)
            : _estimated_size_on_disk(estimated_size_on_disk_),
              _last_flush_duration(last_flush_duration_),
              _estimated_flush_duration(estimated_flush_duration_) {}
        [[nodiscard]] int64_t estimated_size_on_disk() const noexcept { return _estimated_size_on_disk; }
        [[nodiscard]] std::chrono::steady_clock::duration last_flush_duration() const noexcept {
            return _last_flush_duration;
        }
        [[nodiscard]] std::chrono::steady_clock::duration estimated_flush_duration() const noexcept {
            return _estimated_flush_duration;
        }
    };
    DiskIndexes();
    ~DiskIndexes();
    DiskIndexes(const DiskIndexes&) = delete;
    DiskIndexes& operator=(const DiskIndexes&) = delete;
    void setActive(const std::string& index, uint64_t size_on_disk,
                   std::chrono::steady_clock::duration flush_duration);
    void notActive(const std::string& index);
    bool isActive(const std::string& index) const;
    void add_not_active(IndexDiskDir index_disk_dir);
    bool remove(IndexDiskDir index_disk_dir);
    common::ResourceUsage get_resource_usage(const IndexDiskLayout& layout) const;
    uint64_t get_size_on_disk(bool include_stale) const;
    [[nodiscard]] FusionStats calc_fusion_stats() const;
    [[nodiscard]] std::chrono::steady_clock::duration last_flush_duration() const;
    static uint64_t get_size_on_disk_overhead() noexcept;
};

} // namespace searchcorespi::index
