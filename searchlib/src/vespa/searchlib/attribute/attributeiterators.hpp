// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>

namespace search
{


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


} // namespace search
