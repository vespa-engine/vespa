// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "activediskindexes.h"
#include "indexdisklayout.h"
#include "index_disk_dir.h"
#include "index_disk_dir_active_state.h"
#include <vespa/searchlib/util/dirtraverse.h>
#include <cassert>
#include <vector>

using vespalib::string;

namespace searchcorespi::index {

ActiveDiskIndexes::ActiveDiskIndexes() = default;
ActiveDiskIndexes::~ActiveDiskIndexes() = default;

void
ActiveDiskIndexes::setActive(const string &index, uint64_t size_on_disk)
{
    auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(index);
    assert(index_disk_dir.valid());
    std::lock_guard lock(_lock);
    auto insres = _active.insert(std::make_pair(index_disk_dir, IndexDiskDirActiveState()));
    insres.first->second.activate();
    if (!insres.first->second.get_size_on_disk().has_value()) {
        insres.first->second.set_size_on_disk(size_on_disk);
    }
}

void ActiveDiskIndexes::notActive(const string & index) {
    auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(index);
    assert(index_disk_dir.valid());
    std::lock_guard lock(_lock);
    auto it = _active.find(index_disk_dir);
    assert(it != _active.end());
    assert(it->second.is_active());
    it->second.deactivate();
    if (!it->second.is_active()) {
        _active.erase(it);
    }
}

bool ActiveDiskIndexes::isActive(const string &index) const {
    auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(index);
    if (!index_disk_dir.valid()) {
        return false;
    }
    std::lock_guard lock(_lock);
    auto it = _active.find(index_disk_dir);
    return (it != _active.end()) && it->second.is_active();
}


void
ActiveDiskIndexes::add_not_active(IndexDiskDir index_disk_dir)
{
    std::lock_guard lock(_lock);
    _active.insert(std::make_pair(index_disk_dir, IndexDiskDirActiveState()));
}

bool
ActiveDiskIndexes::remove(IndexDiskDir index_disk_dir)
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
    _active.erase(it);
    return true;
}

uint64_t
ActiveDiskIndexes::get_transient_size(IndexDiskLayout& layout, IndexDiskDir index_disk_dir) const
{
    if (!index_disk_dir.valid() || !index_disk_dir.is_fusion_index()) {
        return 0u;
    }
    uint64_t transient_size = 0u;
    std::vector<IndexDiskDir> deferred;
    {
        std::lock_guard lock(_lock);
        for (auto &entry : _active) {
            if (entry.first < index_disk_dir) {
                if (entry.second.get_size_on_disk().has_value()) {
                    transient_size += entry.second.get_size_on_disk().value();
                }
            }
            if (index_disk_dir < entry.first && entry.first.is_fusion_index()) {
                if (entry.second.get_size_on_disk().has_value()) {
                    transient_size += entry.second.get_size_on_disk().value();
                } else {
                    deferred.emplace_back(entry.first);
                }
            }
        }
    }
    for (auto& entry : deferred) {
        auto index_dir = layout.getFusionDir(entry.get_id());
        try {
            search::DirectoryTraverse dirt(index_dir.c_str());
            transient_size += dirt.GetTreeSize();
        } catch (std::exception &) {
        }
    }
    return transient_size;
}

}
