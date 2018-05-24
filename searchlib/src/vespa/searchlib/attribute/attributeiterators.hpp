// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeiterators.h"
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataposition.h>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/objects/visit.h>

namespace search {

template <typename SC>
void
AttributeIteratorBase::and_hits_into(const SC & sc, BitVector & result, uint32_t begin_id) const {
    result.foreach_truebit([&](uint32_t key) { if ( ! sc.matches(key)) { result.clearBit(key); }}, begin_id);
    result.invalidateCachedCount();
}

template <typename SC>
void
AttributeIteratorBase::or_hits_into(const SC & sc, BitVector & result, uint32_t begin_id) const {
    result.foreach_falsebit([&](uint32_t key) { if ( sc.matches(key)) { result.setBit(key); }}, begin_id);
    result.invalidateCachedCount();
}


template <typename SC>
std::unique_ptr<BitVector>
AttributeIteratorBase::get_hits(const SC & sc, uint32_t begin_id) const {
    BitVector::UP result = BitVector::create(begin_id, getEndId());
    for (uint32_t docId(std::max(begin_id, getDocId())); docId < getEndId(); docId++) {
        if (sc.matches(docId)) {
            result->setBit(docId);
        }
    }
    result->invalidateCachedCount();
    return result;
}


template <typename PL>
template <typename... Args>
AttributePostingListIteratorT<PL>::
AttributePostingListIteratorT(bool hasWeight, fef::TermFieldMatchData *matchData, Args &&... args)
    : AttributePostingListIterator(hasWeight, matchData),
      _iterator(std::forward<Args>(args)...),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    setupPostingInfo();
}

template <typename PL>
void AttributePostingListIteratorT<PL>::initRange(uint32_t begin, uint32_t end) {
    AttributePostingListIterator::initRange(begin, end);
    _iterator.lower_bound(begin);
    if (!_iterator.valid() || isAtEnd(_iterator.getKey())) {
        setAtEnd();
    } else {
        setDocId(_iterator.getKey());
    }
}

template <typename PL>
template<typename... Args>
FilterAttributePostingListIteratorT<PL>::
FilterAttributePostingListIteratorT(fef::TermFieldMatchData *matchData, Args &&... args)
    : FilterAttributePostingListIterator(matchData),
      _iterator(std::forward<Args>(args)...),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    setupPostingInfo();
    _matchPosition->setElementWeight(1);
}

template <typename PL>
void  FilterAttributePostingListIteratorT<PL>::initRange(uint32_t begin, uint32_t end) {
    FilterAttributePostingListIterator::initRange(begin, end);
    _iterator.lower_bound(begin);
    if (!_iterator.valid() || isAtEnd(_iterator.getKey())) {
        setAtEnd();
    } else {
        setDocId(_iterator.getKey());
    }
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::doSeek(uint32_t docId)
{
    _iterator.linearSeek(docId);
    if (_iterator.valid()) {
        setDocId(_iterator.getKey());
    } else {
        setAtEnd();
    }
}

template <typename PL>
std::unique_ptr<BitVector>
AttributePostingListIteratorT<PL>::get_hits(uint32_t begin_id) {
    BitVector::UP result(BitVector::create(begin_id, getEndId()));
    for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
        result->setBit(_iterator.getKey());
    }
    result->invalidateCachedCount();
    return result;
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::or_hits_into(BitVector & result, uint32_t begin_id) {
    (void) begin_id;
    for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
        if ( ! result.testBit(_iterator.getKey()) ) {
            result.setBit(_iterator.getKey());
        }
    }
    result.invalidateCachedCount();
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::and_hits_into(BitVector &result, uint32_t begin_id) {
    result.andWith(*get_hits(begin_id));
}

template <typename PL>
std::unique_ptr<BitVector>
FilterAttributePostingListIteratorT<PL>::get_hits(uint32_t begin_id) {
    BitVector::UP result(BitVector::create(begin_id, getEndId()));
    for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
        result->setBit(_iterator.getKey());
    }
    result->invalidateCachedCount();
    return result;
}

template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::or_hits_into(BitVector & result, uint32_t begin_id) {
    (void) begin_id;
    for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
        if ( ! result.testBit(_iterator.getKey()) ) {
            result.setBit(_iterator.getKey());
        }
    }
    result.invalidateCachedCount();
}


template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::and_hits_into(BitVector &result, uint32_t begin_id) {
    result.andWith(*get_hits(begin_id));
}

template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::doSeek(uint32_t docId)
{
    _iterator.linearSeek(docId);
    if (_iterator.valid()) {
        setDocId(_iterator.getKey());
    } else {
        setAtEnd();
    }
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);

    if (_hasWeight) {
        _matchPosition->setElementWeight(getWeight());
    } else {
        uint32_t numOccs(0);
        for(; _iterator.valid() && (_iterator.getKey() == docId); numOccs += getWeight(), ++_iterator);
        _matchPosition->setElementWeight(numOccs);
    }
}

template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
}

template <typename SC>
void
AttributeIteratorT<SC>::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AttributeIterator::visitMembers(visitor);
    visit(visitor, "searchcontext.attribute", _searchContext.attribute().getName());
    visit(visitor, "searchcontext.queryterm", _searchContext.queryTerm());
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    FilterAttributeIterator::visitMembers(visitor);
    visit(visitor, "searchcontext.attribute", _searchContext.attribute().getName());
    visit(visitor, "searchcontext.queryterm", _searchContext.queryTerm());
}

template <typename SC>
AttributeIteratorT<SC>::AttributeIteratorT(const SC &searchContext, fef::TermFieldMatchData *matchData)
    : AttributeIterator(matchData),
      _searchContext(searchContext)
{ }


template <typename SC>
FilterAttributeIteratorT<SC>::FilterAttributeIteratorT(const SC &searchContext, fef::TermFieldMatchData *matchData)
    : FilterAttributeIterator(matchData),
      _searchContext(searchContext)
{ }


template <typename SC>
void
FlagAttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    const SC & sc(_sc);
    const Attribute &attr = static_cast<const Attribute &>(sc.attribute());
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if ((bv != NULL) && !isAtEnd(docId) && bv->testBit(docId)) {
            setDocId(docId);
            return;
        }
    }

    uint32_t minNextBit(search::endDocId);
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if (bv != NULL && !isAtEnd(docId)) {
            uint32_t nextBit = bv->getNextTrueBit(docId);
            minNextBit = std::min(nextBit, minNextBit);
        }
    }
    if (!isAtEnd(minNextBit)) {
        setDocId(minNextBit);
    } else {
        setAtEnd();
    }
}

template <typename SC>
void
FlagAttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    const SC & sc(_sc);
    const Attribute &attr = static_cast<const Attribute &>(sc.attribute());
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if ((bv != NULL) && !isAtEnd(docId) && bv->testBit(docId)) {
            setDocId(docId);
            return;
        }
    }
}

template <typename SC>
void
FlagAttributeIteratorT<SC>::or_hits_into(BitVector &result, uint32_t begin_id) {
    (void) begin_id;
    const SC & sc(_sc);
    const Attribute &attr = static_cast<const Attribute &>(sc.attribute());
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if (bv != NULL) {
            result.orWith(*bv);
        }
    }
}

template <typename SC>
void
FlagAttributeIteratorT<SC>::and_hits_into(BitVector &result, uint32_t begin_id) {
    const SC & sc(_sc);
    const Attribute &attr = static_cast<const Attribute &>(sc.attribute());
    if (sc._low == sc._high) {
        const BitVector * bv = attr.getBitVector(sc._low);
        if (bv != NULL) {
            result.andWith(*bv);
        } else {
            // I would expect us never to end up in this case as we are probably
            // replaced by an EmptySearch, but I keep the code here to be functionally complete.
            result.clear();
        }
    } else {
        SearchIterator::and_hits_into(result, begin_id);
    }
}

template <typename SC>
std::unique_ptr<BitVector>
FlagAttributeIteratorT<SC>::get_hits(uint32_t begin_id) {
    const SC & sc(_sc);
    const Attribute &attr = static_cast<const Attribute &>(sc.attribute());
    int i = sc._low;
    BitVector::UP result;
    for (;!result && i < sc._high; ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if (bv != NULL) {
            result = BitVector::create(*bv, begin_id, getEndId());
        }
    }

    for (; i <= sc._high; ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if (bv != NULL) {
            result->orWith(*bv);
        }
    }
    if (!result) {
        result = BitVector::create(begin_id, getEndId());
    } else if (begin_id < getDocId()) {
        result->clearInterval(begin_id, std::min(getDocId(), getEndId()));
    }

    return result;
}

template <typename SC>
void
AttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (isAtEnd(docId)) {
        setAtEnd();
    } else if (_searchContext.matches(docId, _weight)) {
        setDocId(docId);
    }
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (isAtEnd(docId)) {
        setAtEnd();
    } else if (_searchContext.matches(docId)) {
        setDocId(docId);
    }
}

template <typename SC>
void
AttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    for (uint32_t nextId = docId; !isAtEnd(nextId); ++nextId) {
        if (_searchContext.matches(nextId, _weight)) {
            setDocId(nextId);
            return;
        }
    }
    setAtEnd();
}

template <typename SC>
void
FilterAttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    for (uint32_t nextId = docId; !isAtEnd(nextId); ++nextId) {
        if (_searchContext.matches(nextId)) {
            setDocId(nextId);
            return;
        }
    }
    setAtEnd();
}

template <typename SC>
void
AttributeIteratorT<SC>::or_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::or_hits_into(_searchContext, result, begin_id);
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::or_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::or_hits_into(_searchContext, result, begin_id);
}

template <typename SC>
BitVector::UP
AttributeIteratorT<SC>::get_hits(uint32_t begin_id) {
    return AttributeIteratorBase::get_hits(_searchContext, begin_id);
}

template <typename SC>
BitVector::UP
FilterAttributeIteratorT<SC>::get_hits(uint32_t begin_id) {
    return AttributeIteratorBase::get_hits(_searchContext, begin_id);
}

template <typename SC>
void
AttributeIteratorT<SC>::and_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::and_hits_into(_searchContext, result, begin_id);
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::and_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::and_hits_into(_searchContext, result, begin_id);
}

} // namespace search
