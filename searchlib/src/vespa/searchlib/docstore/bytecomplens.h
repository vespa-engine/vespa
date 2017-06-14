// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/vespalib/data/databuffer.h>

namespace search {

/**
 * Class compressing a table of offsets in memory.
 * After adding (n) offsets you can access
 * (n-1) pairs of (length, offset).
 * All offsets must be increasing, but they
 * may be added in several chunks.
 **/
class ByteCompressedLengths
{
public:
    /**
     * Construct an empty instance
     **/
    ByteCompressedLengths();

    /**
     * add the given offset table.
     * @param entries number of offsets to store.
     * @param offsets pointer to table that contains (entries) offsets.
     **/
    void addOffsetTable(uint64_t entries, uint64_t *offsets);

    /**
     * free resources
     **/
    ~ByteCompressedLengths();

    struct OffLen
    {
        uint64_t offset;
        uint64_t length;
    };

    /**
     * Fetch an offset and length from compressed data.
     * Note restriction: idx must be < size()
     *
     * @param idx The index into the offset table
     * @return offset[id] and the delta (offset[id+1] - offset[id])
     **/
    OffLen getOffLen(uint64_t idx) const;

    /**
     * The number of (length, offset) pairs stored
     * Note that size() == sum(entries) - 1
     **/
    uint64_t size() const { return _entries; }

    /**
     * remove all data from this instance
     **/
    void clear();

    /**
     * swap all data with another instance
     **/
    void swap(ByteCompressedLengths& other);

    /**
     * Calculate memory used by this instance
     * @return memory usage (in bytes)
     **/
    size_t memoryUsed() const;

private:
    struct L3Entry {
        uint64_t offset;
        uint64_t l0toff;
        uint64_t l1toff;
        uint64_t l2toff;
    };
    vespalib::DataBuffer _l0space;
    vespalib::DataBuffer _l1space;
    vespalib::DataBuffer _l2space;

    std::vector<L3Entry> _l3table;

    uint64_t _entries;

    struct ProgressPoint {
        uint64_t lenSum1;
        uint64_t lenSum2;
        uint64_t l0oSum1;
        uint64_t l0oSum2;
        uint64_t l1oSum2;
        uint64_t last_offset;
    } _progress;

    struct CachedPointers {
        const uint8_t *l0table;
        const uint8_t *l1table;
        const uint8_t *l2table;
    } _ptrcache;

    bool _hasInitialOffset;

    void addOffset(uint64_t offset);
};

} // namespace search

