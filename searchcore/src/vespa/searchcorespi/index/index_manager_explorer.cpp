// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_manager_explorer.h"
#include "index_manager_stats.h"
#include <vespa/searchcorespi/index/imemoryindex.h>
#include <vespa/searchcorespi/index/indexsearchablevisitor.h>
#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using search::SearchableStats;
using searchcorespi::index::DiskIndexStats;
using searchcorespi::index::MemoryIndexStats;

namespace searchcorespi {

namespace {

void
insertDiskIndex(Cursor &arrayCursor, const DiskIndexStats &diskIndex)
{
    Cursor &diskIndexCursor = arrayCursor.addObject();
    const SearchableStats &sstats = diskIndex.getSearchableStats();
    diskIndexCursor.setLong("serialNum", diskIndex.getSerialNum());
    diskIndexCursor.setString("indexDir", diskIndex.getIndexdir());
    diskIndexCursor.setLong("sizeOnDisk", sstats.sizeOnDisk());
}

void
insertMemoryUsage(Cursor &object, const vespalib::MemoryUsage &usage)
{
    Cursor &memory = object.setObject("memoryUsage");
    memory.setLong("allocatedBytes", usage.allocatedBytes());
    memory.setLong("usedBytes", usage.usedBytes());
    memory.setLong("deadBytes", usage.deadBytes());
    memory.setLong("onHoldBytes", usage.allocatedBytesOnHold());
}

void
insertMemoryIndex(Cursor &arrayCursor, const MemoryIndexStats &memoryIndex)
{
    Cursor &memoryIndexCursor = arrayCursor.addObject();
    const SearchableStats &sstats = memoryIndex.getSearchableStats();
    memoryIndexCursor.setLong("serialNum", memoryIndex.getSerialNum());
    memoryIndexCursor.setLong("docsInMemory", sstats.docsInMemory());
    insertMemoryUsage(memoryIndexCursor, sstats.memoryUsage());
}

class WriteContextInserter : public IndexSearchableVisitor {
private:
    Cursor& _object;
    bool _has_inserted;

public:
    WriteContextInserter(Cursor& object) : _object(object), _has_inserted(false) {}
    void visit(const index::IDiskIndex&) override {}
    void visit(const index::IMemoryIndex& index) override {
        if (!_has_inserted) {
            index.insert_write_context_state(_object);
            _has_inserted = true;
        }
    }
};

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
        object.setBool("pending_urgent_flush", _mgr->has_pending_urgent_flush());
        Cursor &diskIndexArrayCursor = object.setArray("diskIndexes");
        for (const auto &diskIndex : stats.getDiskIndexes()) {
            insertDiskIndex(diskIndexArrayCursor, diskIndex);
        }
        Cursor &memoryIndexArrayCursor = object.setArray("memoryIndexes");
        for (const auto &memoryIndex : stats.getMemoryIndexes()) {
            insertMemoryIndex(memoryIndexArrayCursor, memoryIndex);
        }
        auto& write_contexts = object.setObject("write_contexts");
        WriteContextInserter visitor(write_contexts);
        _mgr->getSearchable()->accept(visitor);
    }
}

} // namespace searchcorespi
