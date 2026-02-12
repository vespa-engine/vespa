// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_indexes.h"
#include "indexdisklayout.h"
#include "index_disk_dir.h"
#include "index_disk_dir_state.h"
#include <vespa/searchcorespi/common/resource_usage.h>
#include <vespa/searchlib/util/directory_traverse.h>
#include <vespa/searchlib/util/disk_space_calculator.h>
#include <cassert>
#include <vector>

using search::DiskSpaceCalculator;
using searchcorespi::common::ResourceUsage;
using searchcorespi::common::TransientResourceUsage;
using std::string;

namespace searchcorespi::index {

DiskIndexes::DiskIndexes()
    : _active(),
      _sum_size_on_disk(get_size_on_disk_overhead()),
      _sum_stale_size_on_disk(0u),
      _lock()
{
}

DiskIndexes::~DiskIndexes() = default;

void
DiskIndexes::remove_from_sum(const IndexDiskDirState& state)
{
    auto size_on_disk = state.get_size_on_disk().value_or(0u);
    _sum_size_on_disk -= size_on_disk;
    if (state.is_stale()) {
        _sum_stale_size_on_disk -= size_on_disk;
    }
}

void
DiskIndexes::setActive(const string &index, uint64_t size_on_disk)
{
    auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(index);
    assert(index_disk_dir.valid());
    std::lock_guard lock(_lock);
    auto insres = _active.insert(std::make_pair(index_disk_dir, IndexDiskDirState()));
    auto& state = insres.first->second;
    if (state.activate(size_on_disk)) {
        _sum_size_on_disk += size_on_disk;
        if (state.is_stale()) {
            _sum_stale_size_on_disk += size_on_disk;
        }
        if (index_disk_dir.is_fusion_index()) {
            /*
             * Indexes before last active fusion index are on the way out and
             * will be removed when all older index collections
             * referencing them are destroyed. Disk space used by these
             * indexes is considered stale (and transient).
             */
            for (auto& entry : _active) {
                if (entry.first < index_disk_dir) {
                    auto& stale_state = entry.second;
                    if (!stale_state.is_stale()) {
                        stale_state.set_stale();
                        _sum_stale_size_on_disk += stale_state.get_size_on_disk().value_or(0u);
                    }
                } else {
                    break;
                }
            }
        }
    }
}

void DiskIndexes::notActive(const string & index) {
    auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(index);
    assert(index_disk_dir.valid());
    std::lock_guard lock(_lock);
    auto it = _active.find(index_disk_dir);
    assert(it != _active.end());
    auto& state = it->second;
    if (state.deactivate()) {
        remove_from_sum(state);
        _active.erase(it);
    }
}

bool DiskIndexes::isActive(const string &index) const {
    auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(index);
    if (!index_disk_dir.valid()) {
        return false;
    }
    std::lock_guard lock(_lock);
    auto it = _active.find(index_disk_dir);
    return (it != _active.end()) && it->second.is_active();
}

void
DiskIndexes::add_not_active(IndexDiskDir index_disk_dir)
{
    std::lock_guard lock(_lock);
    _active.insert(std::make_pair(index_disk_dir, IndexDiskDirState()));
}

bool
DiskIndexes::remove(IndexDiskDir index_disk_dir)
{
    if (!index_disk_dir.valid()) {
        return true;
    }
    std::lock_guard lock(_lock);
    auto it = _active.find(index_disk_dir);
    if (it == _active.end()) {
        return true;
    }
    if (it->second.is_active()) {
        return false;
    }
     remove_from_sum(it->second);
    _active.erase(it);
    return true;
}

ResourceUsage
DiskIndexes::get_resource_usage(const IndexDiskLayout& layout) const
{
    std::unique_lock guard(_lock);
    uint64_t size_on_disk = _sum_size_on_disk - _sum_stale_size_on_disk;
    uint64_t transient_size = _sum_stale_size_on_disk;
    std::vector<IndexDiskDir> deferred;
    for (auto &entry : _active) {
        auto &state = entry.second;
        /*
         * Indexes after last fusion index can be partially
         * complete and might be removed if fusion is aborted. Disk
         * space used by these indexes is considered transient.
         */
        if (!state.get_size_on_disk().has_value() && !state.is_stale()) {
            deferred.emplace_back(entry.first);
        }
    }
    guard.unlock();
    for (auto& entry : deferred) {
        auto index_dir = layout.getFusionDir(entry.get_id());
        try {
            search::DirectoryTraverse dirt(index_dir.c_str());
            transient_size += dirt.GetTreeSize();
        } catch (std::exception &) {
        }
    }
    return ResourceUsage{TransientResourceUsage{transient_size, 0}, size_on_disk};
}

uint64_t
DiskIndexes::get_size_on_disk(bool include_stale) const
{
    std::lock_guard guard(_lock);
    uint64_t size_on_disk = _sum_size_on_disk;
    if (!include_stale) {
        size_on_disk -= _sum_stale_size_on_disk;
    }
    return size_on_disk;
}

uint64_t
DiskIndexes::get_size_on_disk_overhead() noexcept {
    // The "index" directory under the searchable document subdb directory, e.g. "0.ready/index"
    return DiskSpaceCalculator::directory_placeholder_size();
}

}
