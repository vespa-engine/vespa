// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.activediskindexes");

#include "activediskindexes.h"

using std::set;
using vespalib::string;
using vespalib::LockGuard;

namespace searchcorespi {
namespace index {

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

}  // namespace index
}  // namespace searchcorespi
