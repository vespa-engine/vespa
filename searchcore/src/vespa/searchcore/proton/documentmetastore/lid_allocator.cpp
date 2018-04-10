// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_allocator.h"
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <mutex>

#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore.lid_allocator");

using search::fef::TermFieldMatchDataArray;
using search::queryeval::Blueprint;
using search::queryeval::FieldSpecBaseList;
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
      _lidFreeListConstructed(false),
      _activeLids(size, capacity, genHolder, false, false),
      _numActiveLids(0u)
{

}

LidAllocator::~LidAllocator() {}

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
LidAllocator::ensureSpace(uint32_t newSize,
                          uint32_t newCapacity)
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
        _numActiveLids = _activeLids.count();
    }
}

size_t
LidAllocator::getUsedLidsSize() const
{
    return _usedLids.byteSize();
}

void
LidAllocator::trimHoldLists(generation_t firstUsed)
{
    _holdLids.trimHoldLists(firstUsed, _freeLids);
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
LidAllocator::holdLid(DocId lid,
                      DocId lidLimit,
                      generation_t currentGeneration)
{
    (void) lidLimit;
    assert(holdLidOK(lid, lidLimit));
    assert(isFreeListConstructed());
    assert(lid < _usedLids.size());
    assert(lid < _pendingHoldLids.size());
    assert(_pendingHoldLids.testBit(lid));
    _pendingHoldLids.clearBit(lid);
    _holdLids.add(lid, currentGeneration);
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
    const search::GrowableBitVector &_activeLids;
    const uint32_t _docIdLimit;
    mutable std::mutex _lock;
    mutable std::vector<search::fef::TermFieldMatchData *> _matchDataVector;

    virtual SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda,
                     bool strict) const override
    {
        assert(tfmda.size() == 0);
        (void) tfmda;
        search::fef::TermFieldMatchData *tfmd =
            new search::fef::TermFieldMatchData;
        {
            std::lock_guard<std::mutex> lock(_lock);
            _matchDataVector.push_back(tfmd);
        }
        return search::BitVectorIterator::create(&_activeLids, _docIdLimit, *tfmd, strict);
    }

public:
    WhiteListBlueprint(const search::GrowableBitVector &activeLids, uint32_t docIdLimit)
        : SimpleLeafBlueprint(FieldSpecBaseList()),
          _activeLids(activeLids),
          _docIdLimit(docIdLimit),
          _matchDataVector()
    {
        setEstimate(HitEstimate(_activeLids.size(), false));
    }

    bool isWhiteList() const override { return true; }

    ~WhiteListBlueprint() {
        for (auto matchData : _matchDataVector) {
            delete matchData;
        }
    }
};

}

Blueprint::UP
LidAllocator::createWhiteListBlueprint(uint32_t docIdLimit) const
{
    return std::make_unique<WhiteListBlueprint>(_activeLids.getBitVector(), docIdLimit);
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
        _numActiveLids = _activeLids.count();
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

uint32_t
LidAllocator::getNumUsedLids() const
{
    return _usedLids.count();
}

}
