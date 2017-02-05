// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeiterators.h"
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/searchlib/common/bitvector.h>

namespace search {

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


} // namespace search
