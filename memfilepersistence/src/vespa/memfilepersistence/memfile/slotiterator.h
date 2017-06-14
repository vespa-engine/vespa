// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::SlotIterator
 * \ingroup memfile
 *
 * \brief Utility class for iterating slots in a MemFile.
 *
 * When needing to iterate the slots, sometimes one want to iterate only unique
 * slots and sometimes you want to iterate deleted slots. Iterating only unique
 * slots adds a CPU cost, so one would want to avoid adding that cost if
 * iterating all.
 *
 * To simplify code iterating slots, they can use a SlotIterator, such that they
 * don't have to reimplement the iteration.
 *
 * The typical way of creating such an iterator, is by calling MemFile's
 * getSlotIterator function, which will give you an iterator of suitable
 * implementation. Do not use these directly.
 */

#pragma once

#include <vespa/memfilepersistence/common/types.h>
#include <vespa/vespalib/stllike/hash_set.h>

namespace storage {
namespace memfile {

class MemFile; // MemFile depends on this file. Don't want circular dependency
class MemSlot;

class SlotIterator : protected Types {
protected:
    mutable MemSlot* _current;

    virtual void iterate() const = 0;
    SlotIterator() : _current(0) {}

public:
    typedef std::unique_ptr<SlotIterator> UP;
    typedef std::unique_ptr<const SlotIterator> CUP;

    virtual ~SlotIterator() {}

    virtual SlotIterator* clone() const = 0;

    MemSlot* getCurrent() { return _current; }
    const MemSlot* getCurrent() const { return _current; }

    const MemSlot& operator++() const { iterate(); return *_current; }
};

class GidUniqueSlotIterator : public SlotIterator {
    const MemFile& _file;
    typedef vespalib::hash_set<GlobalId, GlobalId::hash> SeenMap;
    mutable SeenMap _seen;
    bool _iterateRemoves;
    Timestamp _fromTimestamp;
    Timestamp _toTimestamp;
    mutable uint32_t _currentIndex;

public:
    GidUniqueSlotIterator(const MemFile& file,
                          bool iterateRemoves,
                          Timestamp fromTimestamp,
                          Timestamp toTimestamp);

    void iterate() const override;
     SlotIterator* clone() const override;
};

class AllSlotsIterator : public SlotIterator {
    const MemFile& _file;
    bool _iterateRemoves;
    Timestamp _fromTimestamp;
    Timestamp _toTimestamp;
    mutable uint32_t _currentIndex;

public:
    AllSlotsIterator(const MemFile& file,
                     bool iterateRemoves,
                     Timestamp fromTimestamp,
                     Timestamp toTimestamp);

    void iterate() const override;
    SlotIterator* clone() const override;
};

/**
 * \class storage::memfile::IteratorWrapper
 * \ingroup memfile
 *
 * \brief Wrapper class for iterators, such that we can return by value.
 *
 * Iterators use inheritance, so we need a wrapper class to wrap the
 * implementation in order to be able to return iterators by value, as one is
 * acustomed to in the standard library.
 */
class IteratorWrapper {
    SlotIterator::CUP _it;

public:
    IteratorWrapper() {} // Creates end() iterator.
    IteratorWrapper(SlotIterator::CUP it) : _it(std::move(it)) {}
        // Override to clone implementation
    IteratorWrapper(const IteratorWrapper& o) : _it(o._it->clone()) {}
    IteratorWrapper& operator=(const IteratorWrapper& o) {
        _it.reset(0);
        if (o._it.get() != 0) _it.reset(o._it->clone());
        return *this;
    }

    bool operator==(const IteratorWrapper& o) const {
        const MemSlot* slot(_it.get() == 0 ? 0 : _it->getCurrent());
        const MemSlot* slot2(o._it.get() == 0 ? 0 : o._it->getCurrent());
        return (slot == slot2);
    }
    bool operator!=(const IteratorWrapper& o) const {
        return ! (*this == o);
    } 

    const MemSlot& operator*() const { return *_it->getCurrent(); }
    const MemSlot* operator->() const { return _it->getCurrent(); }
    const MemSlot& operator++() const { return ++*_it; }
};


} // memfile
} // storage
