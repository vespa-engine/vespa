// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idiskindex.h"
#include "index_disk_dir.h"
#include <mutex>

namespace searchcorespi::index {

class DiskIndexes;
class IndexDiskLayout;

/*
 * A disk index wrapper that removes the disk index if it is old and unused when the wrapper is destroyed.
 */

class DiskIndexWithDestructorCleanup : public IDiskIndex {
private:
    std::shared_ptr<std::mutex>          _remove_lock;
    std::shared_ptr<IDiskIndex>          _index;
    IndexDiskDir                         _index_disk_dir;
    std::shared_ptr<IndexDiskLayout>     _layout;
    std::shared_ptr<DiskIndexes>         _disk_indexes;

public:
    DiskIndexWithDestructorCleanup(std::shared_ptr<std::mutex> remove_lock,
                                    std::shared_ptr<IDiskIndex> index,
                                    std::shared_ptr<IndexDiskLayout> layout,
                                    std::shared_ptr<DiskIndexes> disk_indexes) noexcept;
    DiskIndexWithDestructorCleanup(const DiskIndexWithDestructorCleanup&) = delete;
    DiskIndexWithDestructorCleanup(DiskIndexWithDestructorCleanup&&) = delete;
    ~DiskIndexWithDestructorCleanup() override;
    DiskIndexWithDestructorCleanup& operator=(const DiskIndexWithDestructorCleanup&) = delete;
    DiskIndexWithDestructorCleanup& operator=(DiskIndexWithDestructorCleanup&&) = delete;
    const IDiskIndex &getWrapped() const { return *_index; }

    /**
     * Implements searchcorespi::IndexSearchable
     */
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext& requestContext,
                    const FieldSpec& field,
                    const Node& term,
                    search::fef::MatchDataLayout& global_layout) override;
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext& requestContext,
                    const FieldSpecList& fields,
                    const Node& term,
                    search::fef::MatchDataLayout& global_layout) override;
    search::IndexStats get_index_stats(bool clear_disk_io_stats) const override;
    search::SerialNum getSerialNum() const override;
    void accept(IndexSearchableVisitor &visitor) const override;

    /**
     * Implements IFieldLengthInspector
     */
    search::index::FieldLengthInfo get_field_length_info(const std::string& field_name) const override;

    /**
     * Implements IDiskIndex
     */
    const std::string& getIndexDir() const override;
    const search::index::Schema& getSchema() const override;
};

}
