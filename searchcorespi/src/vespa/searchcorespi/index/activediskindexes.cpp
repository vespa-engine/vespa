// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "activediskindexes.h"
#include "indexdisklayout.h"
#include "index_disk_dir.h"
#include "index_disk_dir_active_state.h"
#include <cassert>

using vespalib::string;

namespace searchcorespi::index {

ActiveDiskIndexes::ActiveDiskIndexes() = default;
ActiveDiskIndexes::~ActiveDiskIndexes() = default;

void ActiveDiskIndexes::setActive(const string &index) {
    auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(index);
    assert(index_disk_dir.valid());
    std::lock_guard lock(_lock);
    auto insres = _active.insert(std::make_pair(index_disk_dir, IndexDiskDirActiveState()));
    insres.first->second.activate();
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

}
