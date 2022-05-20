// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <mutex>
#include <memory>

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
    mutable std::mutex _lock;

public:
    using SP = std::shared_ptr<DiskIndexes>;
    DiskIndexes();
    ~DiskIndexes();
    DiskIndexes(const DiskIndexes &) = delete;
    DiskIndexes & operator = (const DiskIndexes &) = delete;
    void setActive(const vespalib::string & index, uint64_t size_on_disk);
    void notActive(const vespalib::string & index);
    bool isActive(const vespalib::string & index) const;
    void add_not_active(IndexDiskDir index_disk_dir);
    bool remove(IndexDiskDir index_disk_dir);
    uint64_t get_transient_size(IndexDiskLayout& layout, IndexDiskDir index_disk_dir) const;
};

}
