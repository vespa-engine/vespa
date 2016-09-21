// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.index_manager_explorer");
#include "index_manager_explorer.h"
#include "index_manager_stats.h"

#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using search::SearchableStats;
using searchcorespi::index::DiskIndexStats;
using searchcorespi::index::MemoryIndexStats;

namespace searchcorespi {

namespace {

void insertDiskIndex(Cursor &arrayCursor, const DiskIndexStats &diskIndex)
{
    Cursor &diskIndexCursor = arrayCursor.addObject();
    const SearchableStats &sstats = diskIndex.getSearchableStats();
    diskIndexCursor.setLong("serialNum", diskIndex.getSerialNum());
    diskIndexCursor.setString("indexDir", diskIndex.getIndexdir());
    diskIndexCursor.setLong("sizeOnDisk", sstats.sizeOnDisk());
}

void insertMemoryIndex(Cursor &arrayCursor, const MemoryIndexStats &memoryIndex)
{
    Cursor &memoryIndexCursor = arrayCursor.addObject();
    const SearchableStats &sstats = memoryIndex.getSearchableStats();
    memoryIndexCursor.setLong("serialNum", memoryIndex.getSerialNum());
    memoryIndexCursor.setLong("docsInMemory", sstats.docsInMemory());
    memoryIndexCursor.setLong("memoryUsage", sstats.memoryUsage());
}

}


IndexManagerExplorer::IndexManagerExplorer(IIndexManager::SP mgr)
    : _mgr(std::move(mgr))
{
}

void
IndexManagerExplorer::get_state(const Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    object.setLong("lastSerialNum", _mgr->getCurrentSerialNum());
    if (full) {
        IndexManagerStats stats(*_mgr);
        Cursor &diskIndexArrayCursor = object.setArray("diskIndexes");
        for (const auto &diskIndex : stats.getDiskIndexes()) {
            insertDiskIndex(diskIndexArrayCursor, diskIndex);
        }
        Cursor &memoryIndexArrayCursor = object.setArray("memoryIndexes");
        for (const auto &memoryIndex : stats.getMemoryIndexes()) {
            insertMemoryIndex(memoryIndexArrayCursor, memoryIndex);
        }
    }
}

} // namespace searchcorespi
