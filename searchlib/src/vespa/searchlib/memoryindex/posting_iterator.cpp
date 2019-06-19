// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posting_iterator.h"
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.posting_iterator");

namespace search::memoryindex {

template <bool interleaved_features>
PostingIterator<interleaved_features>::PostingIterator(PostingListIteratorType itr,
                                                       const FeatureStore& featureStore,
                                                       uint32_t packedIndex,
                                                       const fef::TermFieldMatchDataArray& matchData) :
    queryeval::RankedSearchIteratorBase(matchData),
    _itr(itr),
    _featureStore(featureStore),
    _featureDecoder(nullptr)
{
    _featureStore.setupForField(packedIndex, _featureDecoder);
}

template <bool interleaved_features>
PostingIterator<interleaved_features>::~PostingIterator() = default;

template <bool interleaved_features>
void
PostingIterator<interleaved_features>::initRange(uint32_t begin, uint32_t end)
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

template <bool interleaved_features>
void
PostingIterator<interleaved_features>::doSeek(uint32_t docId)
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

template <bool interleaved_features>
void
PostingIterator<interleaved_features>::doUnpack(uint32_t docId)
{
    if (!_matchData.valid() || getUnpacked()) {
        return;
    }
    assert(docId == getDocId());
    assert(_itr.valid());
    assert(docId == _itr.getKey());
    datastore::EntryRef featureRef(_itr.getData().get_features());
    _featureStore.setupForUnpackFeatures(featureRef, _featureDecoder);
    _featureDecoder.unpackFeatures(_matchData, docId);
    setUnpacked();
}

template
class PostingIterator<false>;

}

namespace search::btree {

template class BTreeNodeTT<uint32_t,
                           search::memoryindex::PostingListEntry<false>,
                           search::btree::NoAggregated,
                           BTreeDefaultTraits::INTERNAL_SLOTS>;

template class BTreeLeafNode<uint32_t,
                             search::memoryindex::PostingListEntry<false>,
                             search::btree::NoAggregated,
                             BTreeDefaultTraits::LEAF_SLOTS>;

template class BTreeNodeStore<uint32_t,
                              search::memoryindex::PostingListEntry<false>,
                              search::btree::NoAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;

template class BTreeIteratorBase<uint32_t,
                                 search::memoryindex::PostingListEntry<false>,
                                 search::btree::NoAggregated,
                                 BTreeDefaultTraits::INTERNAL_SLOTS,
                                 BTreeDefaultTraits::LEAF_SLOTS,
                                 BTreeDefaultTraits::PATH_SIZE>;

template class BTreeIterator<uint32_t,
                             search::memoryindex::PostingListEntry<false>,
                             search::btree::NoAggregated,
                             std::less<uint32_t>,
                             BTreeDefaultTraits>;

template class BTree<uint32_t,
                     search::memoryindex::PostingListEntry<false>,
                     search::btree::NoAggregated,
                     std::less<uint32_t>,
                     BTreeDefaultTraits>;

template class BTreeRoot<uint32_t,
                         search::memoryindex::PostingListEntry<false>,
                         search::btree::NoAggregated,
                         std::less<uint32_t>,
                         BTreeDefaultTraits>;

template class BTreeRootBase<uint32_t,
                             search::memoryindex::PostingListEntry<false>,
                             search::btree::NoAggregated,
                             BTreeDefaultTraits::INTERNAL_SLOTS,
                             BTreeDefaultTraits::LEAF_SLOTS>;

template class BTreeNodeAllocator<uint32_t,
                                  search::memoryindex::PostingListEntry<false>,
                                  search::btree::NoAggregated,
                                  BTreeDefaultTraits::INTERNAL_SLOTS,
                                  BTreeDefaultTraits::LEAF_SLOTS>;

}
