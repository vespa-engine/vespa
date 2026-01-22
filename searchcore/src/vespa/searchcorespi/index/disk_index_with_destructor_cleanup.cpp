// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_index_with_destructor_cleanup.h"
#include "disk_indexes.h"
#include "diskindexcleaner.h"
#include "indexdisklayout.h"
#include <vespa/searchlib/queryeval/blueprint.h>

namespace searchcorespi::index {

DiskIndexWithDestructorCleanup::DiskIndexWithDestructorCleanup(std::shared_ptr<std::mutex> remove_lock,
                                                               std::shared_ptr<IDiskIndex> index,
                                                               std::shared_ptr<IndexDiskLayout> layout,
                                                               std::shared_ptr<DiskIndexes> disk_indexes) noexcept
    : _remove_lock(std::move(remove_lock)),
      _index(std::move(index)),
      _index_disk_dir(IndexDiskLayout::get_index_disk_dir(_index->getIndexDir())),
      _layout(std::move(layout)),
      _disk_indexes(std::move(disk_indexes))
{
}

DiskIndexWithDestructorCleanup::~DiskIndexWithDestructorCleanup()
{
    auto index_dir = _index->getIndexDir();
    _index.reset();
    _disk_indexes->notActive(index_dir);
    std::lock_guard remove_guard(*_remove_lock);
    DiskIndexCleaner::removeOldIndexes(_layout->get_base_dir(), *_disk_indexes);
}

std::unique_ptr<search::queryeval::Blueprint>
DiskIndexWithDestructorCleanup::createBlueprint(const IRequestContext& requestContext,
                                                const FieldSpec& field,
                                                const Node& term,
                                                search::fef::MatchDataLayout& global_layout)
{
    FieldSpecList fsl;
    fsl.add(field);
    return _index->createBlueprint(requestContext, fsl, term, global_layout);
}

std::unique_ptr<search::queryeval::Blueprint>
DiskIndexWithDestructorCleanup::createBlueprint(const IRequestContext& requestContext,
                                                const FieldSpecList& fields,
                                                const Node& term,
                                                search::fef::MatchDataLayout& global_layout)
{
    return _index->createBlueprint(requestContext, fields, term, global_layout);
}

search::IndexStats
DiskIndexWithDestructorCleanup::get_index_stats(bool clear_disk_io_stats) const
{
    return _index->get_index_stats(clear_disk_io_stats);
}

search::SerialNum
DiskIndexWithDestructorCleanup::getSerialNum() const
{
    return _index->getSerialNum();
}
void
DiskIndexWithDestructorCleanup::accept(IndexSearchableVisitor& visitor) const
{
    _index->accept(visitor);
}

search::index::FieldLengthInfo
DiskIndexWithDestructorCleanup::get_field_length_info(const std::string& field_name) const
{
    return _index->get_field_length_info(field_name);
}

const std::string&
DiskIndexWithDestructorCleanup::getIndexDir() const
{
    return _index->getIndexDir();
}

const search::index::Schema&
DiskIndexWithDestructorCleanup::getSchema() const
{
    return _index->getSchema();
}

}
