// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <mutex>
#include <memory>

namespace searchcorespi::index {

class IndexDiskDir;
class IndexDiskDirActiveState;
class IndexDiskLayout;

/**
 * Class used to keep track of the set of active disk indexes in an index maintainer.
 * The index directories are used as identifiers.
 */
class ActiveDiskIndexes {
    std::map<IndexDiskDir, IndexDiskDirActiveState> _active;
    mutable std::mutex _lock;

public:
    using SP = std::shared_ptr<ActiveDiskIndexes>;
    ActiveDiskIndexes();
    ~ActiveDiskIndexes();
    ActiveDiskIndexes(const ActiveDiskIndexes &) = delete;
    ActiveDiskIndexes & operator = (const ActiveDiskIndexes &) = delete;
    void setActive(const vespalib::string & index, uint64_t size_on_disk);
    void notActive(const vespalib::string & index);
    bool isActive(const vespalib::string & index) const;
    void add_not_active(IndexDiskDir index_disk_dir);
    bool remove(IndexDiskDir index_disk_dir);
    uint64_t get_transient_size(IndexDiskLayout& layout, IndexDiskDir index_disk_dir) const;
};

}
