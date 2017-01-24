// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_allocator.h"

#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore.lid_allocator");

using search::fef::TermFieldMatchDataArray;
using search::queryeval::Blueprint;
using search::queryeval::FieldSpecBaseList;
using search::queryeval::SimpleLeafBlueprint;
using search::queryeval::SearchIterator;
using search::AttributeVector;
using vespalib::GenerationHolder;
using search::QueryTermSimple;
using search::GrowStrategy;

namespace proton {
namespace documentmetastore {

LidAllocator::LidAllocator(uint32_t size,
                           uint32_t capacity,
                           GenerationHolder &genHolder,
                           const GrowStrategy & grow)
    : _holdLids(),
      _freeLids(size,
                capacity,
                genHolder,
                true,
                false),
      _usedLids(size,
                capacity,
                genHolder,
                false,
                true),
      _pendingHoldLids(size,
                       capacity,
                       genHolder,
                       false,
                       false),
      _lidFreeListConstructed(false),
      _activeLidsAttr(new BitAttribute("[activelids]", grow)),
      _activeLids(static_cast<BitAttribute &>(*_activeLidsAttr)),
      _numActiveLids(0u)
{

}

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
}

void
LidAllocator::ensureSpace(DocId lid,
                          uint32_t newSize,
                          uint32_t newCapacity)
{
    ensureSpace(newSize, newCapacity);
    if (lid >= _activeLids.getNumDocs()) {
        _activeLids.addDocs((lid - _activeLids.getNumDocs()) + 1);
    }
    _activeLids.commit();
}

void
LidAllocator::unregisterLid(DocId lid)
{
    assert(!_pendingHoldLids.testBit(lid));
    if (isFreeListConstructed()) {
        _pendingHoldLids.setBit(lid);
    }
    _usedLids.clearBit(lid);
    if (_activeLids.get(lid) != 0) {
        --_numActiveLids;
    }
    _activeLids.update(lid, 0);
    _activeLids.commit();
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
    _activeLids.update(toLid, _activeLids.get(fromLid));
    _activeLids.update(fromLid, 0);
    _activeLids.commit();
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

class BlackListBlueprint : public SimpleLeafBlueprint
{
private:
    AttributeVector::SearchContext::UP _searchCtx;
    vespalib::Lock _lock;
    mutable std::vector<search::fef::TermFieldMatchData *> _matchDataVector;

    virtual SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda,
                     bool strict) const
    {
        assert(tfmda.size() == 0);
        (void) tfmda;
        search::fef::TermFieldMatchData *tfmd =
            new search::fef::TermFieldMatchData;
        {
            vespalib::LockGuard lock(_lock);
            _matchDataVector.push_back(tfmd);
        }
        return _searchCtx->createIterator(tfmd, strict);
    }

    virtual void
    fetchPostings(bool strict)
    {
        _searchCtx->fetchPostings(strict);
    }

public:
    BlackListBlueprint(AttributeVector::SearchContext::UP searchCtx)
        : SimpleLeafBlueprint(FieldSpecBaseList()),
          _searchCtx(std::move(searchCtx)),
          _matchDataVector()
    {
        setEstimate(HitEstimate(0, false));
    }

    ~BlackListBlueprint() {
        for (auto matchData : _matchDataVector) {
            delete matchData;
        }
    }
};

}

Blueprint::UP
LidAllocator::createBlackListBlueprint() const
{
    QueryTermSimple::UP term(new QueryTermSimple("0", QueryTermSimple::WORD));
    return Blueprint::UP(
            new BlackListBlueprint(_activeLids.getSearch(std::move(term),
                                                         AttributeVector::SearchContext::Params())));
}

void
LidAllocator::updateActiveLids(DocId lid, bool active)
{
    int8_t oldActiveFlag = _activeLids.get(lid);
    int8_t newActiveFlag = (active ? 1 : 0);
    if (oldActiveFlag != newActiveFlag) {
        if (oldActiveFlag != 0) {
            if (newActiveFlag == 0) {
                --_numActiveLids;
            }
        } else {
            ++_numActiveLids;
        }
    }
    _activeLids.update(lid, newActiveFlag);
}

void
LidAllocator::commitActiveLids()
{
    _activeLids.commit();
}

void
LidAllocator::clearDocs(DocId lidLow, DocId lidLimit)
{
    (void) lidLow;
    (void) lidLimit;
    assert(_usedLids.getNextTrueBit(lidLow) >= lidLimit);
}

void
LidAllocator::compactLidSpace(uint32_t wantedLidLimit)
{
    _activeLids.compactLidSpace(wantedLidLimit);
}

void
LidAllocator::shrinkLidSpace(DocId committedDocIdLimit)
{
    _activeLids.shrinkLidSpace();
    ensureSpace(committedDocIdLimit, committedDocIdLimit);
}

uint32_t
LidAllocator::getNumUsedLids() const
{
    return _usedLids.count();
}

} // namespace documentmetastore
} // namespace proton
