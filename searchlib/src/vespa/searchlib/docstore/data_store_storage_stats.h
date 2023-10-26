// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search {

/*
 * Class representing brief stats for a data store.
 */
class DataStoreStorageStats
{
    uint64_t _diskUsage;
    uint64_t _diskBloat;
    double   _maxBucketSpread;
    uint64_t _lastSerialNum;
    uint64_t _lastFlushedSerialNum;
    uint32_t _docIdLimit;
public:
    DataStoreStorageStats(uint64_t diskUsage_in, uint64_t diskBloat_in, double maxBucketSpread_in,
                          uint64_t lastSerialNum_in, uint64_t lastFlushedSerialNum_in, uint32_t docIdLimit_in)
        : _diskUsage(diskUsage_in),
          _diskBloat(diskBloat_in),
          _maxBucketSpread(maxBucketSpread_in),
          _lastSerialNum(lastSerialNum_in),
          _lastFlushedSerialNum(lastFlushedSerialNum_in),
          _docIdLimit(docIdLimit_in)
    { }
    uint64_t diskUsage() const            { return _diskUsage; }
    uint64_t diskBloat() const            { return _diskBloat; }
    double   maxBucketSpread() const      { return _maxBucketSpread; }
    uint64_t lastSerialNum() const        { return _lastSerialNum; }
    uint64_t lastFlushedSerialNum() const { return _lastFlushedSerialNum; }
    uint32_t docIdLimit() const           { return _docIdLimit; }
};

} // namespace search
