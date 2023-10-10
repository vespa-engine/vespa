// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_store_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using search::DataStoreFileChunkStats;
using search::DataStoreStorageStats;

namespace proton {

DocumentStoreExplorer::DocumentStoreExplorer(ISummaryManager::SP mgr)
    : _mgr(std::move(mgr))
{
}

namespace {

void
setMemoryUsage(Cursor &object, const vespalib::MemoryUsage &usage)
{
    Cursor &memory = object.setObject("memoryUsage");
    memory.setLong("allocatedBytes", usage.allocatedBytes());
    memory.setLong("usedBytes", usage.usedBytes());
    memory.setLong("deadBytes", usage.deadBytes());
    memory.setLong("onHoldBytes", usage.allocatedBytesOnHold());
}

}

void
DocumentStoreExplorer::get_state(const Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    search::IDocumentStore &store = _mgr->getBackingStore();
    DataStoreStorageStats storageStats(store.getStorageStats());
    object.setLong("diskUsage", storageStats.diskUsage());
    object.setLong("diskBloat", storageStats.diskBloat());
    object.setDouble("maxBucketSpread", storageStats.maxBucketSpread());
    object.setLong("lastFlushedSerialNum", storageStats.lastFlushedSerialNum());
    object.setLong("lastSerialNum", storageStats.lastSerialNum());
    object.setLong("docIdLimit", storageStats.docIdLimit());
    setMemoryUsage(object, store.getMemoryUsage());
    if (full) {
        const vespalib::string &baseDir = store.getBaseDir();
        std::vector<DataStoreFileChunkStats> chunks;
        chunks = store.getFileChunkStats();
        Cursor &fileChunksArrayCursor = object.setArray("fileChunks");
        for (const auto &chunk : chunks) {
            Cursor &chunkCursor = fileChunksArrayCursor.addObject();
            chunkCursor.setLong("diskUsage", chunk.diskUsage());
            chunkCursor.setLong("diskBloat", chunk.diskBloat());
            chunkCursor.setDouble("bucketSpread", chunk.maxBucketSpread());
            chunkCursor.setLong("lastFlushedSerialNum", chunk.lastFlushedSerialNum());
            chunkCursor.setLong("lastSerialNum", chunk.lastSerialNum());
            chunkCursor.setLong("docIdLimit", chunk.docIdLimit());
            chunkCursor.setLong("nameid", chunk.nameId());
            chunkCursor.setString("name", chunk.createName(baseDir));
        }
    }
}

} // namespace proton
