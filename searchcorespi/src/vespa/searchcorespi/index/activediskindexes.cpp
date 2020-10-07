// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "activediskindexes.h"
#include <cassert>

using std::set;
using vespalib::string;
using vespalib::LockGuard;

namespace searchcorespi::index {

void ActiveDiskIndexes::setActive(const string &index) {
    LockGuard lock(_lock);
    _active.insert(index);
}

void ActiveDiskIndexes::notActive(const string & index) {
    LockGuard lock(_lock);
    set<string>::iterator it = _active.find(index);
    assert(it != _active.end());
    _active.erase(it);
}

bool ActiveDiskIndexes::isActive(const string &index) const {
    LockGuard lock(_lock);
    return _active.find(index) != _active.end();
}

}
