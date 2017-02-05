// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

template <typename PL>
AttributePostingListIteratorT<PL>::
AttributePostingListIteratorT(PL &iterator,
                              bool hasWeight,
                              fef::TermFieldMatchData *matchData)
    : AttributePostingListIterator(hasWeight, matchData),
      _iterator(),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    _iterator.swap(iterator);
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
FilterAttributePostingListIteratorT<PL>::
FilterAttributePostingListIteratorT(PL &iterator, fef::TermFieldMatchData *matchData)
    : FilterAttributePostingListIterator(matchData),
      _iterator(),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    _iterator.swap(iterator);
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
FilterAttributePostingListIteratorT<PL>::get_hits(uint32_t begin_id) {
    BitVector::UP result(BitVector::create(begin_id, getEndId()));
    for (; _iterator.getKey() < getEndId(); ++_iterator) {
        result->setBit(_iterator.getKey());
    }
    return result;
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
        : AttributeIterator(matchData, searchContext._attr.getCommittedDocIdLimit()),
          _searchContext(searchContext)
{ }


template <typename SC>
FilterAttributeIteratorT<SC>::FilterAttributeIteratorT(const SC &searchContext, fef::TermFieldMatchData *matchData)
        : FilterAttributeIterator(matchData, searchContext._attr.getCommittedDocIdLimit()),
          _searchContext(searchContext)
{ }


template <typename SC>
void
FlagAttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    const SC & sc(_sc);
    const typename SC::Attribute &attr =
            static_cast<const typename SC::Attribute &>(sc.attribute());
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if ((bv != NULL) && docId < _docIdLimit && bv->testBit(docId)) {
            setDocId(docId);
            return;
        }
    }

    uint32_t minNextBit(search::endDocId);
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if (bv != NULL && docId < _docIdLimit) {
            uint32_t nextBit = bv->getNextTrueBit(docId);
            minNextBit = std::min(nextBit, minNextBit);
        }
    }
    if (minNextBit < _docIdLimit) {
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
    const typename SC::Attribute &attr =
            static_cast<const typename SC::Attribute &>(sc.attribute());
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = attr.getBitVector(i);
        if ((bv != NULL) && docId < _docIdLimit && bv->testBit(docId)) {
            setDocId(docId);
            return;
        }
    }
}

template <typename SC>
void
AttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
    } else if (_searchContext.cmp(docId, _weight)) {
        setDocId(docId);
    }
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
    } else if (_searchContext.cmp(docId)) {
        setDocId(docId);
    }
}

template <typename SC>
void
AttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    for (uint32_t nextId = docId; nextId < _docIdLimit; ++nextId) {
        if (_searchContext.cmp(nextId, _weight)) {
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
    for (uint32_t nextId = docId; nextId < _docIdLimit; ++nextId) {
        if (_searchContext.cmp(nextId)) {
            setDocId(nextId);
            return;
        }
    }
    setAtEnd();
}

} // namespace search
