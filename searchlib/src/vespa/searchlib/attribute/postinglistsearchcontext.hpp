// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dociditerator.h"
#include "attributeiterators.h"
#include "diversity.h"
#include "postingstore.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/common/growablebitvector.h>
#include "posting_list_traverser.h"


using search::queryeval::EmptySearch;
using search::queryeval::SearchIterator;

namespace search::attribute {

template <typename DataT>
PostingListSearchContextT<DataT>::
PostingListSearchContextT(const Dictionary &dictionary, uint32_t docIdLimit, uint64_t numValues, bool hasWeight,
                          const PostingList &postingList, const EnumStoreBase &esb,
                          uint32_t minBvDocFreq, bool useBitVector)
    : PostingListSearchContext(dictionary, docIdLimit, numValues, hasWeight, esb, minBvDocFreq, useBitVector),
      _postingList(postingList),
      _merger(docIdLimit),
      _fetchPostingsDone(false)
{
}

template <typename DataT>
PostingListSearchContextT<DataT>::~PostingListSearchContextT() {}


template <typename DataT>
void
PostingListSearchContextT<DataT>::lookupSingle()
{
    PostingListSearchContext::lookupSingle();
    if (!_pidx.valid())
        return;
    uint32_t typeId = _postingList.getTypeId(_pidx);
    if (!_postingList.isSmallArray(typeId)) {
        if (_postingList.isBitVector(typeId)) {
            const BitVectorEntry *bve = _postingList.getBitVectorEntry(_pidx);
            const GrowableBitVector *bv = bve->_bv.get();
            if (_useBitVector) {
                _gbv = bv;
            } else {
                _pidx = bve->_tree;
                if (_pidx.valid()) { 
                    auto frozenView = _postingList.getTreeEntry(_pidx)->getFrozenView(_postingList.getAllocator());
                    _frozenRoot = frozenView.getRoot();
                    if (!_frozenRoot.valid()) {
                        _pidx = datastore::EntryRef();
                    }
                } else {
                    _gbv = bv; 
                }
            }
        } else {
            auto frozenView = _postingList.getTreeEntry(_pidx)->getFrozenView(_postingList.getAllocator());
            _frozenRoot = frozenView.getRoot();
            if (!_frozenRoot.valid()) {
                _pidx = datastore::EntryRef();
            }
        }
    }
}


template <typename DataT>
size_t
PostingListSearchContextT<DataT>::countHits() const
{
    size_t sum(0);
    for (auto it(_lowerDictItr); it != _upperDictItr; ++it) {
        if (useThis(it)) {
            sum += _postingList.frozenSize(it.getData());
        }
    }
    return sum;
}


template <typename DataT>
void
PostingListSearchContextT<DataT>::fillArray()
{
    for (auto it(_lowerDictItr); it != _upperDictItr; ++it) {
        if (useThis(it)) {
            _merger.addToArray(PostingListTraverser<PostingList>(_postingList, it.getData()));
        }
    }
    _merger.merge();
}


template <typename DataT>
void
PostingListSearchContextT<DataT>::fillBitVector()
{
    for (auto it(_lowerDictItr); it != _upperDictItr; ++it) {
        if (useThis(it)) {
            _merger.addToBitVector(PostingListTraverser<PostingList>(_postingList, it.getData()));
        }
    }
}


template <typename DataT>
void
PostingListSearchContextT<DataT>::fetchPostings(bool strict)
{
    assert (! _fetchPostingsDone);

    _fetchPostingsDone = true;
    if (_uniqueValues < 2u) {
        return;
    }
    if (strict && !fallbackToFiltering()) {
        size_t sum(countHits());
        if (sum < _docIdLimit / 64) {
            _merger.reserveArray(_uniqueValues, sum);
            fillArray();
        } else {
            _merger.allocBitVector();
            fillBitVector();
        }
        _merger.merge();
    }
}


template <typename DataT>
void
PostingListSearchContextT<DataT>::diversify(bool forward, size_t wanted_hits, const IAttributeVector &diversity_attr,
                                            size_t max_per_group, size_t cutoff_groups, bool cutoff_strict)
{
    assert(!_fetchPostingsDone);
    _fetchPostingsDone = true;
    _merger.reserveArray(128, wanted_hits);
    diversity::diversify(forward, _lowerDictItr, _upperDictItr, _postingList, wanted_hits, diversity_attr,
                         max_per_group, cutoff_groups, cutoff_strict, _merger.getWritableArray(), _merger.getWritableStartPos());
    _merger.merge();
}


template <typename DataT>
SearchIterator::UP
PostingListSearchContextT<DataT>::
createPostingIterator(fef::TermFieldMatchData *matchData, bool strict)
{
    assert(_fetchPostingsDone);
    if (_uniqueValues == 0u) {
        return SearchIterator::UP(new EmptySearch());
    }
    if (_merger.hasArray() || _merger.hasBitVector()) { // synthetic results are available
        if (!_merger.emptyArray()) {
            assert(_merger.hasArray());
            using DocIt = DocIdIterator<Posting>;
            DocIt postings;
            vespalib::ConstArrayRef<Posting> array = _merger.getArray();
            postings.set(&array[0], &array[array.size()]);
            if (_postingList._isFilter) {
                return std::make_unique<FilterAttributePostingListIteratorT<DocIt>>(matchData, postings);
            } else {
                return std::make_unique<AttributePostingListIteratorT<DocIt>>(_hasWeight, matchData, postings);
            }
        }
        if (_merger.hasArray()) {
            return SearchIterator::UP(new EmptySearch());
        }
        const BitVector *bv(_merger.getBitVector());
        assert(bv != nullptr);
        return search::BitVectorIterator::create(bv, bv->size(), *matchData, strict);
    }
    if (_uniqueValues == 1) {
        if (_gbv != nullptr) {
            return BitVectorIterator::create(_gbv, std::min(_gbv->size(), _docIdLimit), *matchData, strict);
        }
        if (!_pidx.valid()) {
            return SearchIterator::UP(new EmptySearch());
        }
        const PostingList &postingList = _postingList;
        if (!_frozenRoot.valid()) {
            uint32_t clusterSize = _postingList.getClusterSize(_pidx);
            assert(clusterSize != 0);
            using DocIt = DocIdMinMaxIterator<Posting>;
            DocIt postings;
            const Posting *array = postingList.getKeyDataEntry(_pidx, clusterSize);
            postings.set(array, array + clusterSize);
            if (postingList._isFilter) {
                return std::make_unique<FilterAttributePostingListIteratorT<DocIt>>(matchData, postings);
            } else {
                return std::make_unique<AttributePostingListIteratorT<DocIt>>(_hasWeight, matchData, postings);
            }
        }
        typename PostingList::BTreeType::FrozenView frozen(_frozenRoot, postingList.getAllocator());

        using DocIt = typename PostingList::ConstIterator;
        if (_postingList._isFilter) {
            return std::make_unique<FilterAttributePostingListIteratorT<DocIt>>(matchData, frozen.getRoot(), frozen.getAllocator());
        } else {
            return std::make_unique<AttributePostingListIteratorT<DocIt>> (_hasWeight, matchData, frozen.getRoot(), frozen.getAllocator());
        }
    }
    // returning nullptr will trigger fallback to filter iterator
    return SearchIterator::UP();
}


template <typename DataT>
unsigned int
PostingListSearchContextT<DataT>::singleHits() const
{
    if (_gbv) {
        // Some inaccuracy is expected, data changes underfeet
        return _gbv->countTrueBits();
    }
    if (!_pidx.valid()) {
        return 0u;
    }
    if (!_frozenRoot.valid()) {
        return _postingList.getClusterSize(_pidx);
    }
    typename PostingList::BTreeType::FrozenView frozenView(_frozenRoot, _postingList.getAllocator());
    return frozenView.size();
}

template <typename DataT>
unsigned int
PostingListSearchContextT<DataT>::approximateHits() const
{
    unsigned int numHits = 0;
    if (_uniqueValues == 0u) {
    } else if (_uniqueValues == 1u) {
        numHits = singleHits();
    } else {
        if (this->fallbackToFiltering()) {
            numHits = _docIdLimit;
        } else if (_uniqueValues > MIN_UNIQUE_VALUES_BEFORE_APPROXIMATION) {
            if ((_uniqueValues * MIN_UNIQUE_VALUES_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION > static_cast<int>(_docIdLimit)) ||
                (this->calculateApproxNumHits() * MIN_APPROXHITS_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION > _docIdLimit) ||
                (_uniqueValues > MIN_UNIQUE_VALUES_BEFORE_APPROXIMATION*10))
            {
                numHits = this->calculateApproxNumHits();
            } else {
                // XXX: Unsafe
                numHits = countHits();
            }
        } else {
            // XXX: Unsafe
            numHits = countHits();
        }
    }
    return numHits;
}


template <typename DataT>
void
PostingListSearchContextT<DataT>::applyRangeLimit(int rangeLimit)
{
    if (rangeLimit > 0) {
        DictionaryConstIterator middle = _lowerDictItr;
        for (int n(0); (n < rangeLimit) && (middle != _upperDictItr); ++middle) {
            n += _postingList.frozenSize(middle.getData());
        }
        _upperDictItr = middle;
        _uniqueValues = _upperDictItr - _lowerDictItr;
    } else if ((rangeLimit < 0) && (_lowerDictItr != _upperDictItr)) {
        rangeLimit = -rangeLimit;
        DictionaryConstIterator middle = _upperDictItr;
        for (int n(0); (n < rangeLimit) && (middle != _lowerDictItr); ) {
            --middle;
            n += _postingList.frozenSize(middle.getData());
        }
        _lowerDictItr = middle;
        _uniqueValues = _upperDictItr - _lowerDictItr;
    }
}


template <typename DataT>
PostingListFoldedSearchContextT<DataT>::
PostingListFoldedSearchContextT(const Dictionary &dictionary, uint32_t docIdLimit, uint64_t numValues,
                                bool hasWeight, const PostingList &postingList, const EnumStoreBase &esb,
                                uint32_t minBvDocFreq, bool useBitVector)
    : Parent(dictionary, docIdLimit, numValues, hasWeight, postingList, esb, minBvDocFreq, useBitVector)
{
}


template <typename DataT>
unsigned int
PostingListFoldedSearchContextT<DataT>::approximateHits() const
{
    unsigned int numHits = 0;
    if (_uniqueValues == 0u) {
    } else if (_uniqueValues == 1u) {
        numHits = singleHits();
    } else {
        if (this->fallbackToFiltering()) {
            numHits = _docIdLimit;
        } else {
            // XXX: Unsafe
            numHits = countHits();
        }
    }
    return numHits;
}

}
