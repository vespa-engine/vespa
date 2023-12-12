// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postinglistsearchcontext.h"
#include "array_iterator.h"
#include "attributeiterators.h"
#include "diversity.h"
#include "postingstore.hpp"
#include "posting_list_traverser.h"
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/common/growablebitvector.h>


using search::queryeval::EmptySearch;
using search::queryeval::SearchIterator;

namespace search::attribute {

template <typename DataT>
PostingListSearchContextT<DataT>::
PostingListSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues, bool hasWeight,
                          const PostingStore& posting_store, bool useBitVector, const ISearchContext &searchContext)
    : PostingListSearchContext(dictionary, dictionary.get_has_btree_dictionary(), docIdLimit, numValues, hasWeight, useBitVector, searchContext),
      _posting_store(posting_store),
      _merger(docIdLimit)
{
}

template <typename DataT>
PostingListSearchContextT<DataT>::~PostingListSearchContextT() = default;


template <typename DataT>
void
PostingListSearchContextT<DataT>::lookupSingle()
{
    PostingListSearchContext::lookupSingle();
    if (!_pidx.valid())
        return;
    uint32_t typeId = _posting_store.getTypeId(_pidx);
    if (!_posting_store.isSmallArray(typeId)) {
        if (_posting_store.isBitVector(typeId)) {
            const BitVectorEntry *bve = _posting_store.getBitVectorEntry(_pidx);
            const GrowableBitVector *bv = bve->_bv.get();
            _bv = &bv->reader();
            _pidx = bve->_tree;
        }
        if (_pidx.valid()) {
            auto frozenView = _posting_store.getTreeEntry(_pidx)->getFrozenView(_posting_store.getAllocator());
            _frozenRoot = frozenView.getRoot();
            if (!_frozenRoot.valid()) {
                _pidx = vespalib::datastore::EntryRef();
            }
        }
    }
}

template <typename DataT>
void
PostingListSearchContextT<DataT>::fillArray()
{
    for (auto it(_lowerDictItr); it != _upperDictItr; ++it) {
        _merger.addToArray(PostingListTraverser<PostingStore>(_posting_store, it.getData().load_acquire()));
    }
    _merger.merge();
}

template <typename DataT>
void
PostingListSearchContextT<DataT>::fillBitVector()
{
    for (auto it(_lowerDictItr); it != _upperDictItr; ++it) {
        _merger.addToBitVector(PostingListTraverser<PostingStore>(_posting_store, it.getData().load_acquire()));
    }
}

template <typename DataT>
void
PostingListSearchContextT<DataT>::fetchPostings(const queryeval::ExecuteInfo & execInfo)
{
    // The following constant is derived after running parts of
    // the range search performance test with 10M documents on an Apple M1 Pro with 32 GB memory.
    // This code was compiled with two different strategies:
    //   1) 'always array merging'
    //   2) 'always bitvector merging'
    // https://github.com/vespa-engine/system-test/tree/master/tests/performance/range_search
    //
    // The following 33 test cases were used:
    // range_hits_ratio=[1, 5, 6, 7, 8, 9, 10, 20, 30, 40, 50], values_in_range=[1, 100, 10000], fast_search=true, filter_hits_ratio=0.
    //
    // The baseline performance is given by values_in_range=1, as this uses a single posting list.
    // The total cost of posting list merging is the difference in avg query latency (ms) between the baseline and the case in question.
    // Based on perf analysis we observe that the cost of iterating the posting list entries and inserting them into
    // either an array or bitvector is equal.
    // The differences however are:
    //  1) Merging sorted array segments (one per posting list) into one large sorted array.
    //  2) Allocating the memory needed for the bitvector.
    //
    // The cost of the two strategies is modeled as:
    //  1) estimated_hits_in_range * X
    //  2) docIdLimit * Y
    //
    // Based on the performance results we calculate average values for X and Y:
    //  1) X = Array merging cost per hit = 32 ns
    //  2) Y = Memory allocation cost per document = 0.08 ns
    //
    // The threshold for when to use array merging is therefore 0.0025 (0.08 / 32).
    constexpr float threshold_for_using_array = 0.0025;
    if (!_merger.merge_done() && _uniqueValues >= 2u && this->_dictionary.get_has_btree_dictionary()) {
        if (execInfo.is_strict() || use_posting_lists_when_non_strict(execInfo)) {
            size_t sum = estimated_hits_in_range();
            if (sum < (_docIdLimit * threshold_for_using_array)) {
                _merger.reserveArray(_uniqueValues, sum);
                fillArray();
            } else {
                _merger.allocBitVector();
                fillBitVector();
            }
            _merger.merge();
        }
    }
}


template <typename DataT>
void
PostingListSearchContextT<DataT>::diversify(bool forward, size_t wanted_hits, const IAttributeVector &diversity_attr,
                                            size_t max_per_group, size_t cutoff_groups, bool cutoff_strict)
{
    if (!_merger.merge_done()) {
        _merger.reserveArray(128, wanted_hits);
        if (_uniqueValues == 1u && !_lowerDictItr.valid() && _pidx.valid()) {
            diversity::diversify_single(_pidx, _posting_store, wanted_hits, diversity_attr,
                                        max_per_group, cutoff_groups, cutoff_strict, _merger.getWritableArray(), _merger.getWritableStartPos());
        } else {
            diversity::diversify(forward, _lowerDictItr, _upperDictItr, _posting_store, wanted_hits, diversity_attr,
                                 max_per_group, cutoff_groups, cutoff_strict, _merger.getWritableArray(), _merger.getWritableStartPos());
        }
        _merger.merge();
    }
}


template <typename DataT>
SearchIterator::UP
PostingListSearchContextT<DataT>::
createPostingIterator(fef::TermFieldMatchData *matchData, bool strict)
{
    if (_uniqueValues == 0u) {
        return std::make_unique<EmptySearch>();
    }
    if (_merger.hasArray() || _merger.hasBitVector()) { // synthetic results are available
        if (!_merger.emptyArray()) {
            assert(_merger.hasArray());
            using DocIt = ArrayIterator<Posting>;
            DocIt postings;
            vespalib::ConstArrayRef<Posting> array = _merger.getArray();
            postings.set(&array[0], &array[array.size()]);
            if (_posting_store.isFilter()) {
                return std::make_unique<FilterAttributePostingListIteratorT<DocIt>>(_baseSearchCtx, matchData, postings);
            } else {
                return std::make_unique<AttributePostingListIteratorT<DocIt>>(_baseSearchCtx, _hasWeight, matchData, postings);
            }
        }
        if (_merger.hasArray()) {
            return std::make_unique<EmptySearch>();
        }
        const BitVector *bv(_merger.getBitVector());
        assert(bv != nullptr);
        return search::BitVectorIterator::create(bv, bv->size(), *matchData, strict);
    }
    if (_uniqueValues == 1) {
        if (_bv != nullptr && (!_pidx.valid() || _useBitVector || matchData->isNotNeeded())) {
            return BitVectorIterator::create(_bv, std::min(_bv->size(), _docIdLimit), *matchData, strict);
        }
        if (!_pidx.valid()) {
            return std::make_unique<EmptySearch>();
        }
        if (!_frozenRoot.valid()) {
            uint32_t clusterSize = _posting_store.getClusterSize(_pidx);
            assert(clusterSize != 0);
            using DocIt = DocIdMinMaxIterator<Posting>;
            DocIt postings;
            const Posting *array = _posting_store.getKeyDataEntry(_pidx, clusterSize);
            postings.set(array, array + clusterSize);
            if (_posting_store.isFilter()) {
                return std::make_unique<FilterAttributePostingListIteratorT<DocIt>>(_baseSearchCtx, matchData, postings);
            } else {
                return std::make_unique<AttributePostingListIteratorT<DocIt>>(_baseSearchCtx, _hasWeight, matchData, postings);
            }
        }
        typename PostingStore::BTreeType::FrozenView frozen(_frozenRoot, _posting_store.getAllocator());

        using DocIt = typename PostingStore::ConstIterator;
        if (_posting_store.isFilter()) {
            return std::make_unique<FilterAttributePostingListIteratorT<DocIt>>(_baseSearchCtx, matchData, frozen.getRoot(), frozen.getAllocator());
        } else {
            return std::make_unique<AttributePostingListIteratorT<DocIt>> (_baseSearchCtx, _hasWeight, matchData, frozen.getRoot(), frozen.getAllocator());
        }
    }
    // returning nullptr will trigger fallback to filter iterator
    return {};
}


template <typename DataT>
unsigned int
PostingListSearchContextT<DataT>::singleHits() const
{
    if (_bv && !_pidx.valid()) {
        // Some inaccuracy is expected, data changes underfeet
        return _bv->countTrueBits();
    }
    if (!_pidx.valid()) {
        return 0u;
    }
    if (!_frozenRoot.valid()) {
        return _posting_store.getClusterSize(_pidx);
    }
    typename PostingStore::BTreeType::FrozenView frozenView(_frozenRoot, _posting_store.getAllocator());
    return frozenView.size();
}

template <typename DataT>
unsigned int
PostingListSearchContextT<DataT>::approximateHits() const
{
    size_t numHits = 0;
    if (_uniqueValues == 0u) {
    } else if (_uniqueValues == 1u) {
        numHits = singleHits();
    } else if (_dictionary.get_has_btree_dictionary()) {
        numHits = estimated_hits_in_range();
    } else {
        numHits = _docIdLimit;
    }
    return std::min(numHits, size_t(std::numeric_limits<uint32_t>::max()));
}

template <typename DataT>
void
PostingListSearchContextT<DataT>::applyRangeLimit(long rangeLimit)
{
    long n = 0;
    size_t count = 0;
    if (rangeLimit > 0) {
        DictionaryConstIterator middle = _lowerDictItr;
        for (; (n < rangeLimit) && (count < max_posting_lists_to_count) && (middle != _upperDictItr); ++middle, count++) {
            n += _posting_store.frozenSize(middle.getData().load_acquire());
        }
        if (middle == _upperDictItr) {
            // All there is
        } else if (n >= rangeLimit) {
            _upperDictItr = middle;
        } else {
            size_t offset = ((rangeLimit - n) * count)/n;
            middle += offset;
            if (middle.valid() && ((_upperDictItr - middle) > 0)) {
                _upperDictItr = middle;
            }
        }
    } else if ((rangeLimit < 0) && (_lowerDictItr != _upperDictItr)) {
        rangeLimit = -rangeLimit;
        DictionaryConstIterator middle = _upperDictItr;
        for (; (n < rangeLimit) && (count < max_posting_lists_to_count) && (middle != _lowerDictItr); count++) {
            --middle;
            n += _posting_store.frozenSize(middle.getData().load_acquire());
        }
        if (middle == _lowerDictItr) {
            // All there is
        } else if (n >= rangeLimit) {
            _lowerDictItr = middle;
        } else {
            size_t offset = ((rangeLimit - n) * count)/n;
            middle -= offset;
            if (middle.valid() && ((middle - _lowerDictItr) > 0)) {
                _lowerDictItr = middle;
            }
        }
    }
    _uniqueValues = std::abs(_upperDictItr - _lowerDictItr);
}


template <typename DataT>
PostingListFoldedSearchContextT<DataT>::
PostingListFoldedSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                                bool hasWeight, const PostingStore& posting_store,
                                bool useBitVector, const ISearchContext &searchContext)
    : Parent(dictionary, docIdLimit, numValues, hasWeight, posting_store, useBitVector, searchContext),
      _resume_scan_itr(),
      _posting_indexes()
{
}

template <typename DataT>
PostingListFoldedSearchContextT<DataT>::~PostingListFoldedSearchContextT() = default;

template <typename DataT>
size_t
PostingListFoldedSearchContextT<DataT>::calc_estimated_hits_in_range() const
{
    size_t sum = 0;
    bool overflow = false;
    for (auto it(_lowerDictItr); it != _upperDictItr;) {
        if (use_dictionary_entry(it)) {
            auto pidx = it.getData().load_acquire();
            if (pidx.valid()) {
                sum += _posting_store.frozenSize(pidx);
                if (!overflow) {
                    if (_posting_indexes.size() < MAX_POSTING_INDEXES_SIZE) {
                        _posting_indexes.emplace_back(pidx);
                    } else {
                        overflow = true;
                        _resume_scan_itr = it;
                    }
                }
            }
            ++it;
        }
    }
    return sum;
}

template <typename DataT>
template <bool fill_array>
void
PostingListFoldedSearchContextT<DataT>::fill_array_or_bitvector_helper(EntryRef pidx)
{
    if constexpr (fill_array) {
        _merger.addToArray(PostingListTraverser<PostingStore>(_posting_store, pidx));
    } else {
        _merger.addToBitVector(PostingListTraverser<PostingStore>(_posting_store, pidx));
    }
}

template <typename DataT>
template <bool fill_array>
void
PostingListFoldedSearchContextT<DataT>::fill_array_or_bitvector()
{
    for (auto pidx : _posting_indexes) {
        fill_array_or_bitvector_helper<fill_array>(pidx);
    }
    if (_resume_scan_itr.valid()) {
        for (auto it(_resume_scan_itr); it != _upperDictItr;) {
            if (use_dictionary_entry(it)) {
                auto pidx = it.getData().load_acquire();
                if (pidx.valid()) {
                    fill_array_or_bitvector_helper<fill_array>(pidx);
                }
                ++it;
            }
        }
    }
    _merger.merge();
}

template <typename DataT>
void
PostingListFoldedSearchContextT<DataT>::fillArray()
{
    fill_array_or_bitvector<true>();
}

template <typename DataT>
void
PostingListFoldedSearchContextT<DataT>::fillBitVector()
{
    fill_array_or_bitvector<false>();
}

}
