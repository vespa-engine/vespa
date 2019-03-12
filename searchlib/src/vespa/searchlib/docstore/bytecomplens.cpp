// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bytecomplens.h"

#include <vespa/log/log.h>
LOG_SETUP(".search.docstore");

namespace search {

static inline uint64_t getBCN(const uint8_t *&buffer) __attribute__((__always_inline__));

/**
 * get "Byte Compressed Number" from buffer, incrementing pointer
 **/
static inline uint64_t getBCN(const uint8_t *&buffer)
{
    uint8_t b = *buffer++;
    uint64_t len = (b & 127);
    unsigned shiftLen = 0;
    while (b & 128) {
        shiftLen += 7;
        b = *buffer++;
        len |= ((b & 127) << shiftLen);
    }
    return len;
}

static size_t writeLen(vespalib::DataBuffer &buf, uint64_t len)
{
    size_t bytes = 0;
    do {
        uint8_t b = len & 127;
        len >>= 7;
        if (len > 0) {
            b |= 128;
        }
        buf.writeInt8(b);
        ++bytes;
    } while (len > 0);
    return bytes;
}


ByteCompressedLengths::ByteCompressedLengths()
    : _l0space(),
      _l1space(),
      _l2space(),
      _l3table(),
      _entries(0),
      _progress(),
      _ptrcache(),
      _hasInitialOffset(false)
{
    clear();
}


void
ByteCompressedLengths::clear()
{
    _l0space.clear();
    _l1space.clear();
    _l2space.clear();
    _l3table.clear();

    _entries = 0;

    _progress.lenSum1 = 0;
    _progress.lenSum2 = 0;
    _progress.l0oSum1 = 0;
    _progress.l0oSum2 = 0;
    _progress.l1oSum2 = 0;
    _progress.last_offset = 0;

    _ptrcache.l0table = NULL;
    _ptrcache.l1table = NULL;
    _ptrcache.l2table = NULL;

    _hasInitialOffset = false;
}


void
ByteCompressedLengths::swap(ByteCompressedLengths& other)
{
    _l0space.swap(other._l0space);
    _l1space.swap(other._l1space);
    _l2space.swap(other._l2space);
    _l3table.swap(other._l3table);

    std::swap(_entries, other._entries);
    std::swap(_progress, other._progress);
    std::swap(_ptrcache, other._ptrcache);
    std::swap(_hasInitialOffset, other._hasInitialOffset);
}


// add a new offset to the compressed tables
void
ByteCompressedLengths::addOffset(uint64_t offset)
{
    assert(offset >= _progress.last_offset);

    // delta from last offset:
    uint64_t len = offset - _progress.last_offset;

    // which entry is this:
    uint64_t idx = _entries++;

    if ((idx & 31) == 0) {
        // add entry to some skip-table
        _progress.lenSum2 += _progress.lenSum1; // accumulate to Level2
        _progress.l0oSum2 += _progress.l0oSum1; // accumulate to Level2

        uint64_t t1n = idx >> 5;
        if ((t1n & 31) == 0) {
            // add Level2 or Level3 table entry:
            uint64_t t2n = t1n >> 5;

            if ((t2n & 31) == 0) {
                // add new Level3 table entry:
                L3Entry e;
                e.offset = _progress.last_offset;
                e.l0toff = _l0space.getDataLen();
                e.l1toff = _l1space.getDataLen();
                e.l2toff = _l2space.getDataLen();

                _l3table.push_back(e);
            } else {
                // write to Level2 table, sums since last reset:
                writeLen(_l2space, _progress.lenSum2); // sum of Level0 lengths
                writeLen(_l2space, _progress.l0oSum2); // sum size of Level0 entries
                writeLen(_l2space, _progress.l1oSum2); // sum size of Level1 entries
            }
            // reset Level2 sums:
            _progress.lenSum2 = 0;
            _progress.l0oSum2 = 0;
            _progress.l1oSum2 = 0;
        } else {
            // write to Level1 table, sums since last reset:
            _progress.l1oSum2 += writeLen(_l1space, _progress.lenSum1); // sum of Level0 lengths
            _progress.l1oSum2 += writeLen(_l1space, _progress.l0oSum1); // sum size of Level0 entries
        }
        // reset Level1 sums:
        _progress.lenSum1 = 0;
        _progress.l0oSum1 = 0;
    }
    // always write length (offset delta) to Level0 table:
    _progress.l0oSum1 += writeLen(_l0space, len);  // accumulate to Level1
    _progress.lenSum1 += len;                      // accumulate to Level1
    _progress.last_offset = offset;
}


void
ByteCompressedLengths::addOffsetTable(uint64_t entries, uint64_t *offsets)
{
    // ignore NOP:
    if (entries == 0) return;

    // Do we have some offsets already?
    if (_hasInitialOffset) {
        // yes, add first offset normally
        addOffset(offsets[0]);
    } else {
        // no, special treatment for very first offset
        _progress.last_offset = offsets[0];
        _hasInitialOffset = true;
    }
    for (uint64_t cnt = 1; cnt < entries; ++cnt) {
        addOffset(offsets[cnt]);
    }

    // Simplify access to actual data:
    _ptrcache.l0table = (uint8_t *)_l0space.getData();
    _ptrcache.l1table = (uint8_t *)_l1space.getData();
    _ptrcache.l2table = (uint8_t *)_l2space.getData();

    // some statistics available when debug logging:
    LOG(debug, "compressed %" PRIu64 " offsets", (_entries+1));
    LOG(debug, "(%" PRIu64 " bytes)", (_entries+1)*sizeof(uint64_t));
    LOG(debug, "to (%ld + %ld + %ld) bytes + %ld l3entries",
        _l0space.getDataLen(),
        _l1space.getDataLen(),
        _l2space.getDataLen(),
        _l3table.size());
    LOG(debug, "(%ld bytes)",
        (_l0space.getDataLen() + _l1space.getDataLen() + _l2space.getDataLen() +
         _l3table.size()*sizeof(L3Entry)));
}


ByteCompressedLengths::~ByteCompressedLengths()
{
}

ByteCompressedLengths::OffLen
ByteCompressedLengths::getOffLen(uint64_t idx) const
{
    assert(idx < _entries);

    unsigned skipL0 = idx & 31;
    unsigned skipL1 = (idx >> 5) & 31;
    unsigned skipL2 = (idx >> 10) & 31;
    uint64_t skipL3 = (idx >> 15);

    uint64_t offset = _l3table[skipL3].offset;
    uint64_t l0toff = _l3table[skipL3].l0toff;
    uint64_t l1toff = _l3table[skipL3].l1toff;
    uint64_t l2toff = _l3table[skipL3].l2toff;

    // printf("start off %ld l0off %ld l1off %ld l2off %ld\n", offset, l0toff, l1toff, l2toff);

    const uint8_t *l2pos = _ptrcache.l2table + l2toff;

    while (skipL2 > 0) {
        --skipL2;
        offset += getBCN(l2pos);
        l0toff += getBCN(l2pos);
        l1toff += getBCN(l2pos);
    }

    const uint8_t *l1pos = _ptrcache.l1table + l1toff;

    while (skipL1 > 0) {
        --skipL1;
        offset += getBCN(l1pos);
        l0toff += getBCN(l1pos);

    }
    const uint8_t *l0pos = _ptrcache.l0table + l0toff;

    while (skipL0 > 0) {
        --skipL0;
        offset += getBCN(l0pos);
    }
    // printf("end off %ld l0off %ld l1off %ld l2off %ld\n", offset, l0toff, l1toff, l2toff);
    OffLen retval;
    retval.offset = offset;
    retval.length = getBCN(l0pos);
    return retval;
}


size_t
ByteCompressedLengths::memoryUsed() const
{
    size_t mem = sizeof(*this);
    mem += _l0space.getBufSize();
    mem += _l1space.getBufSize();
    mem += _l2space.getBufSize();
    mem += _l3table.capacity() * sizeof(L3Entry);
    return mem;
}




} // namespace search

