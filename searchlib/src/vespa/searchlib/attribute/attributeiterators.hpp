// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include "attributeiterators.h"
#include <vespa/searchlib/query/queryterm.h>

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

} // namespace search
