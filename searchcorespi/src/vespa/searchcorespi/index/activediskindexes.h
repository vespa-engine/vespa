// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <set>
#include <mutex>
#include <memory>

namespace searchcorespi::index {

/**
 * Class used to keep track of the set of active disk indexes in an index maintainer.
 * The index directories are used as identifiers.
 */
class ActiveDiskIndexes {
    std::multiset<vespalib::string> _active;
    mutable std::mutex _lock;

public:
    using SP = std::shared_ptr<ActiveDiskIndexes>;
    ActiveDiskIndexes();
    ~ActiveDiskIndexes();
    ActiveDiskIndexes(const ActiveDiskIndexes &) = delete;
    ActiveDiskIndexes & operator = (const ActiveDiskIndexes &) = delete;
    void setActive(const vespalib::string & index);
    void notActive(const vespalib::string & index);
    bool isActive(const vespalib::string & index) const;
};

}
