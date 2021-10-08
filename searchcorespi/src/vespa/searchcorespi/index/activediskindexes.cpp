// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "activediskindexes.h"
#include <cassert>

using vespalib::string;

namespace searchcorespi::index {

ActiveDiskIndexes::ActiveDiskIndexes() = default;
ActiveDiskIndexes::~ActiveDiskIndexes() = default;

void ActiveDiskIndexes::setActive(const string &index) {
    std::lock_guard lock(_lock);
    _active.insert(index);
}

void ActiveDiskIndexes::notActive(const string & index) {
    std::lock_guard lock(_lock);
    auto it = _active.find(index);
    assert(it != _active.end());
    _active.erase(it);
}

bool ActiveDiskIndexes::isActive(const string &index) const {
    std::lock_guard lock(_lock);
    return _active.find(index) != _active.end();
}

}
