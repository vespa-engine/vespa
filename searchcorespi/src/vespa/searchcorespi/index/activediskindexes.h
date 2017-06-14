// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/sync.h>
#include <set>

namespace searchcorespi {
namespace index {

/**
 * Class used to keep track of the set of active disk indexes in an index maintainer.
 * The index directories are used as identifiers.
 */
class ActiveDiskIndexes {
    std::multiset<vespalib::string> _active;
    vespalib::Lock _lock;

public:
    typedef std::shared_ptr<ActiveDiskIndexes> SP;

    void setActive(const vespalib::string & index);
    void notActive(const vespalib::string & index);
    bool isActive(const vespalib::string & index) const;
};

}  // namespace index
}  // namespace searchcorespi

