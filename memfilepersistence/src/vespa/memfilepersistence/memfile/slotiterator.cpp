// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slotiterator.h"
#include <vespa/memfilepersistence/memfile/memfile.h>

namespace storage {
namespace memfile {

GidUniqueSlotIterator::GidUniqueSlotIterator(const MemFile& file,
                                             bool iterateRemoves,
                                             Timestamp fromTimestamp,
                                             Timestamp toTimestamp)
    : _file(file),
      _seen(2 * file.getSlotCount()),
      _iterateRemoves(iterateRemoves),
      _fromTimestamp(fromTimestamp),
      _toTimestamp(toTimestamp),
      _currentIndex(file.getSlotCount())
{
    iterate();
}

void
GidUniqueSlotIterator::iterate() const
{
    for (uint32_t i = _currentIndex - 1; i < _currentIndex; --i) {
            // To avoid separate implementations for const and non-const
            // iterators we do a const cast here. For const iterators, only
            // const MemSlot entries will be exposed externally, so no
            // modifications will be allowed for those.
        MemSlot& slot(const_cast<MemSlot&>(_file[i]));
        if (_fromTimestamp != Timestamp(0) &&
            slot.getTimestamp() < _fromTimestamp) continue;
        if (_toTimestamp != Timestamp(0) &&
            slot.getTimestamp() > _toTimestamp) continue;

        SeenMap::insert_result inserted(_seen.insert(slot.getGlobalId()));
        if (!inserted.second) {
            continue;
        }
        if (slot.deleted() && !_iterateRemoves) continue;
        _current = &slot;
        _currentIndex = i;
        return;
    }
    _current = 0;
    _currentIndex = 0;
}

SlotIterator*
GidUniqueSlotIterator::clone() const {
    GidUniqueSlotIterator* sit(
            new GidUniqueSlotIterator(_file, _iterateRemoves,
                                      _fromTimestamp, _toTimestamp));
    sit->_seen = _seen;
    sit->_currentIndex = _currentIndex;
    sit->_current = _current;
    return sit;
}

AllSlotsIterator::AllSlotsIterator(const MemFile& file,
                                   bool iterateRemoves,
                                   Timestamp fromTimestamp,
                                   Timestamp toTimestamp)
    : _file(file),
      _iterateRemoves(iterateRemoves),
      _fromTimestamp(fromTimestamp),
      _toTimestamp(toTimestamp),
      _currentIndex(file.getSlotCount())
{
    iterate();
}

SlotIterator*
AllSlotsIterator::clone() const {
    AllSlotsIterator* sit = new AllSlotsIterator(_file, _iterateRemoves,
                                                 _fromTimestamp, _toTimestamp);
    sit->_currentIndex = _currentIndex;
    sit->_current = _current;
    return sit;
}

void
AllSlotsIterator::iterate() const
{
    for (uint32_t i = _currentIndex - 1; i < _currentIndex; --i) {
            // To avoid seprate implementations for const and non-const
            // iterators we do a const cast here. For const iterators, only
            // const MemSlot entries will be exposed externally, so no
            // modifications will be allowed for those.
        MemSlot& slot(const_cast<MemSlot&>(_file[i]));
        if (_fromTimestamp != Timestamp(0) &&
            slot.getTimestamp() < _fromTimestamp) continue;
        if (_toTimestamp != Timestamp(0) &&
            slot.getTimestamp() > _toTimestamp) continue;
        if (slot.deleted() && !_iterateRemoves) continue;
        _current = &slot;
        _currentIndex = i;
        return;
    }
    _current = 0;
    _currentIndex = 0;
}

} // memfile
} // storage
