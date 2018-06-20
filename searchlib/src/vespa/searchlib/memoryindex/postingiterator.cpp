// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postingiterator.h"
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.postingiterator");

namespace search {
namespace memoryindex {

PostingIterator::PostingIterator(Dictionary::PostingList::ConstIterator itr,
                                 const FeatureStore & featureStore,
                                 uint32_t packedIndex,
                                 const fef::TermFieldMatchDataArray & matchData) :
    queryeval::RankedSearchIteratorBase(matchData),
    _itr(itr),
    _featureStore(featureStore),
    _featureDecoder(NULL)
{
    _featureStore.setupForField(packedIndex, _featureDecoder);
}

PostingIterator::~PostingIterator() {}

void
PostingIterator::initRange(uint32_t begin, uint32_t end)
{
    SearchIterator::initRange(begin, end);
    _itr.lower_bound(begin);
    if (!_itr.valid() || isAtEnd(_itr.getKey())) {
        setAtEnd();
    } else {
        setDocId(_itr.getKey());
    }
    clearUnpacked();
}

void
PostingIterator::doSeek(uint32_t docId)
{
    if (getUnpacked()) {
        clearUnpacked();
    }
    _itr.linearSeek(docId);
    if (!_itr.valid()) {
        setAtEnd();
    } else {
        setDocId(_itr.getKey());
    }
}

void
PostingIterator::doUnpack(uint32_t docId)
{
    if (!_matchData.valid() || getUnpacked()) {
        return;
    }
    assert(docId == getDocId());
    assert(_itr.valid());
    assert(docId == _itr.getKey());
    datastore::EntryRef featureRef(_itr.getData());
    _featureStore.setupForUnpackFeatures(featureRef, _featureDecoder);
    _featureDecoder.unpackFeatures(_matchData, docId);
    setUnpacked();
}


} // namespace search::memoryindex
} // namespace search

