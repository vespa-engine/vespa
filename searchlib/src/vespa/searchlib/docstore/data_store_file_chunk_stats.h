// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "data_store_storage_stats.h"
#include "data_store_file_chunk_id.h"

namespace search {

/*
 * Class representing stats for the underlying file for a data store.
 */
class DataStoreFileChunkStats : public DataStoreStorageStats,
                                public DataStoreFileChunkId
{
public:
    DataStoreFileChunkStats(uint64_t diskUsage_in, uint64_t diskBloat_in,
                            double maxBucketSpread_in,
                            uint64_t lastSerialNum_in,
                            uint64_t lastFlushedSerialNum_in,
                            uint32_t docIdLimit_in,
                            uint64_t nameId_in)
        : DataStoreStorageStats(diskUsage_in, diskBloat_in,
                                maxBucketSpread_in, lastSerialNum_in,
                                lastFlushedSerialNum_in, docIdLimit_in),
          DataStoreFileChunkId(nameId_in)
    {
    }
};

} // namespace search
