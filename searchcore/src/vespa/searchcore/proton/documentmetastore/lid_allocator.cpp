// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_allocator.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/full_search.h>
#include <mutex>

#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore.lid_allocator");

using search::fef::TermFieldMatchDataArray;
using search::queryeval::Blueprint;
using search::queryeval::FieldSpecBaseList;
using search::queryeval::FullSearch;
using search::queryeval::SearchIterator;
using search::queryeval::SimpleLeafBlueprint;
using vespalib::GenerationHolder;

namespace proton::documentmetastore {

LidAllocator::LidAllocator(uint32_t size,
                           uint32_t capacity,
                           GenerationHolder &genHolder)
    : _holdLids(),
      _freeLids(size, capacity, genHolder, true, false),
      _usedLids(size, capacity, genHolder, false, true),
      _pendingHoldLids(size, capacity, genHolder, false, false),
      _activeLids(size, capacity, genHolder, false, false),
      _numActiveLids(0u),
      _lidFreeListConstructed(false)
{ }

vespalib::MemoryUsage
LidAllocator::getMemoryUsage() const {
    vespalib::MemoryUsage usage;
    size_t allocated = sizeof(*this) + _freeLids.byteSize() + _usedLids.byteSize() +
                       _pendingHoldLids.byteSize() + _activeLids.byteSize() + _holdLids.size();
    usage.incAllocatedBytes(allocated);
    usage.incUsedBytes(allocated);
    return usage;
}

LidAllocator::~LidAllocator() = default;

LidAllocator::DocId
LidAllocator::getFreeLid(DocId lidLimit)
{
    DocId lid = _freeLids.getLowest();
    if (lid >= lidLimit) {
        lid = lidLimit;
    } else {
        _freeLids.clearBit(lid);
    }
    return lid;
}

LidAllocator::DocId
LidAllocator::peekFreeLid(DocId lidLimit)
{
    DocId lid = _freeLids.getLowest();
    if (lid >= lidLimit) {
        lid = lidLimit;
    }
    return lid;
}

void
LidAllocator::ensureSpace(uint32_t newSize, uint32_t newCapacity)
{
    _freeLids.resizeVector(newSize, newCapacity);
    _usedLids.resizeVector(newSize, newCapacity);
    _pendingHoldLids.resizeVector(newSize, newCapacity);
    _activeLids.resizeVector(newSize, newCapacity);
}

void
LidAllocator::unregisterLid(DocId lid)
{
    assert(!_pendingHoldLids.testBit(lid));
    if (isFreeListConstructed()) {
        _pendingHoldLids.setBit(lid);
    }
    _usedLids.clearBit(lid);
    if (_activeLids.testBit(lid)) {
        _activeLids.clearBit(lid);
        _numActiveLids.store(_activeLids.count(), std::memory_order_relaxed);
    }
}

void
LidAllocator::unregister_lids(const std::vector<DocId>& lids)
{
    if (lids.empty()) {
        return;
    }
    auto high = isFreeListConstructed() ? _pendingHoldLids.set_bits(lids) : _pendingHoldLids.assert_not_set_bits(lids);
    assert(high < _usedLids.size());
    _usedLids.clear_bits(lids);
    assert(high < _activeLids.size());
    _activeLids.consider_clear_bits(lids);
    _numActiveLids.store(_activeLids.count(), std::memory_order_relaxed);
}

void
LidAllocator::moveLidBegin(DocId fromLid, DocId toLid)
{
    (void) fromLid;
    assert(!_pendingHoldLids.testBit(fromLid));
    assert(!_pendingHoldLids.testBit(toLid));
    if (isFreeListConstructed()) {
        assert(!_freeLids.testBit(fromLid));
        assert(_freeLids.testBit(toLid));
        _freeLids.clearBit(toLid);
    }
}

void
LidAllocator::moveLidEnd(DocId fromLid, DocId toLid)
{
    if (isFreeListConstructed()) {
        // old lid must be scheduled for hold by caller
        _pendingHoldLids.setBit(fromLid);
    }
    _usedLids.setBit(toLid);
    _usedLids.clearBit(fromLid);
    if (_activeLids.testBit(fromLid)) {
        _activeLids.setBit(toLid);
        _activeLids.clearBit(fromLid);
    }
}

void
LidAllocator::holdLids(const std::vector<DocId> &lids,
                       DocId lidLimit,
                       generation_t currentGeneration)
{
    (void) lidLimit;
    for (const auto &lid : lids) {
        assert(lid > 0);
        assert(holdLidOK(lid, lidLimit));
        _pendingHoldLids.clearBit(lid);
        _holdLids.add(lid, currentGeneration);
    }
}

bool
LidAllocator::holdLidOK(DocId lid, DocId lidLimit) const
{
    if (_lidFreeListConstructed &&
        lid != 0 &&
        lid < lidLimit &&
        lid < _usedLids.size() &&
        lid < _pendingHoldLids.size() &&
        _pendingHoldLids.testBit(lid))
    {
        return true;
    }
    LOG(error,
        "LidAllocator::holdLidOK(%u, %u): "
        "_lidFreeListConstructed=%s, "
        "_usedLids.size()=%d, "
        "_pendingHoldLids.size()=%d, "
        "_pendingHoldLids bit=%s",
        lid, lidLimit,
        _lidFreeListConstructed ? "true" : "false",
        (int) _usedLids.size(),
        (int) _pendingHoldLids.size(),
        lid < _pendingHoldLids.size() ?
        (_pendingHoldLids.testBit(lid) ?
         "true" : "false" ) : "invalid"
        );
    return false;
}

void
LidAllocator::constructFreeList(DocId lidLimit)
{
    assert(!isFreeListConstructed());
    _holdLids.clear();
    for (uint32_t lid = 1; lid < lidLimit; ++lid) {
        if (!validLid(lid)) {
            _freeLids.setBit(lid);
        }
    }
}

namespace {

class WhiteListBlueprint : public SimpleLeafBlueprint
{
private:
    const search::BitVector &_activeLids;
    bool _all_lids_active;
    mutable std::mutex _lock;
    mutable std::vector<search::fef::TermFieldMatchData *> _matchDataVector;

    std::unique_ptr<SearchIterator> create_search_helper(bool strict) const {
        auto tfmd = new search::fef::TermFieldMatchData;
        {
            std::lock_guard<std::mutex> lock(_lock);
            _matchDataVector.push_back(tfmd);
        }
        return search::BitVectorIterator::create(&_activeLids, get_docid_limit(), *tfmd, strict);
    }
    SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override
    {
        assert(tfmda.size() == 0);
        (void) tfmda;
        return create_search_helper(strict);
    }
public:
    WhiteListBlueprint(const search::BitVector &activeLids, bool all_lids_active)
        : SimpleLeafBlueprint(FieldSpecBaseList()),
          _activeLids(activeLids),
          _all_lids_active(all_lids_active),
          _lock(),
          _matchDataVector()
    {
        setEstimate(HitEstimate(_activeLids.size(), false));
    }

    bool isWhiteList() const override { return true; }

    SearchIterator::UP createFilterSearch(bool strict, FilterConstraint) const override {
        if (_all_lids_active) {
            return std::make_unique<FullSearch>();
        }
        return create_search_helper(strict);
    }

    ~WhiteListBlueprint() {
        for (auto matchData : _matchDataVector) {
            delete matchData;
        }
    }
};

}

Blueprint::UP
LidAllocator::createWhiteListBlueprint() const
{
    return std::make_unique<WhiteListBlueprint>(_activeLids.getBitVector(),
                                                (getNumUsedLids() == getNumActiveLids()));
}

void
LidAllocator::updateActiveLids(DocId lid, bool active)
{
    bool oldActive = _activeLids.testBit(lid);
    if (oldActive != active) {
        if (active) {
            _activeLids.setBit(lid);
        } else {
            _activeLids.clearBit(lid);
        }
        _numActiveLids.store(_activeLids.count(), std::memory_order_relaxed);
    }
}

void
LidAllocator::clearDocs(DocId lidLow, DocId lidLimit)
{
    (void) lidLow;
    (void) lidLimit;
    assert(_usedLids.getNextTrueBit(lidLow) >= lidLimit);
}

void
LidAllocator::shrinkLidSpace(DocId committedDocIdLimit)
{
    ensureSpace(committedDocIdLimit, committedDocIdLimit);
}

}
