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
struct PostingListSearchContextT<DataT>::FillPart : public vespalib::Runnable {
    FillPart(const vespalib::Doom & doom, const PostingStore& posting_store, const DictionaryConstIterator & from,
             size_t count, uint32_t limit)
        : FillPart(doom, posting_store, from, count, nullptr, limit)
    { }
    FillPart(const vespalib::Doom & doom, const PostingStore& posting_store, const DictionaryConstIterator & from,
             size_t count, BitVector * bv, uint32_t limit)
        : _doom(doom),
          _posting_store(posting_store),
          _bv(bv),
          _docIdLimit(limit),
          _from(from),
          _to(from),
          _owned_bv()
    {
        _to += count;
    }
    void run() override {
        if (_bv == nullptr) {
            _owned_bv = BitVector::create(_docIdLimit);
            _bv = _owned_bv.get();
        }
        //TODO Add  && !_doom.soft_doom() to loop
        for ( ;_from != _to; ++_from) {
            addToBitVector(PostingListTraverser<PostingStore>(_posting_store, _from.getData().load_acquire()));
        }
    }
    void addToBitVector(const PostingListTraverser<PostingStore> & postingList) {
        postingList.foreach_key([this](uint32_t key) {
            if (__builtin_expect(key < _docIdLimit, true)) { _bv->setBit(key); }
        });
    }
    const vespalib::Doom       _doom;
    const PostingStore        &_posting_store;
    BitVector                 *_bv;
    uint32_t                   _docIdLimit;
    DictionaryConstIterator    _from;
    DictionaryConstIterator    _to;
    std::unique_ptr<BitVector> _owned_bv;
};

template <typename DataT>
void
PostingListSearchContextT<DataT>::fillBitVector(const ExecuteInfo & exec_info)
{
    vespalib::ThreadBundle & thread_bundle = exec_info.thread_bundle();
    size_t num_iter = _upperDictItr - _lowerDictItr;
    size_t num_threads = std::min(thread_bundle.size(), num_iter);

    uint32_t per_thread = num_iter / num_threads;
    uint32_t rest_docs = num_iter % num_threads;
    std::vector<FillPart> parts;
    parts.reserve(num_threads);
    BitVector * master = _merger.getBitVector();
    parts.emplace_back(exec_info.doom(), _posting_store, _lowerDictItr, per_thread + (rest_docs > 0), master, _merger.getDocIdLimit());
    for (size_t i(1); i < num_threads; i++) {
        size_t num_this_thread = per_thread + (i < rest_docs);
        parts.emplace_back(exec_info.doom(), _posting_store, parts[i-1]._to, num_this_thread, _merger.getDocIdLimit());
    }
    thread_bundle.run(parts);
    std::vector<BitVector *> vectors;
    vectors.reserve(parts.size());
    for (const auto & part : parts) {
        vectors.push_back(part._bv);
    }
    BitVector::parallellOr(thread_bundle, vectors);
}

template <typename DataT>
void
PostingListSearchContextT<DataT>::fetchPostings(const ExecuteInfo & exec_info, bool strict)
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
        if (strict || use_posting_lists_when_non_strict(exec_info)) {
            size_t sum = estimated_hits_in_range();
            //TODO Honour soft_doom and forward it to merge code
            if (sum < (_docIdLimit * threshold_for_using_array)) {
                _merger.reserveArray(_uniqueValues, sum);
                fillArray();
            } else {
                _merger.allocBitVector();
                fillBitVector(exec_info);
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
        return BitVectorIterator::create(bv, bv->size(), *matchData, strict);
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
HitEstimate
PostingListSearchContextT<DataT>::calc_hit_estimate() const
{
    size_t numHits = 0;
    if (_uniqueValues == 0u) {
    } else if (_uniqueValues == 1u) {
        numHits = singleHits();
    } else if (_dictionary.get_has_btree_dictionary()) {
        numHits = estimated_hits_in_range();
    } else {
        return HitEstimate::unknown(_docIdLimit);
    }
    return HitEstimate(std::min(numHits, size_t(std::numeric_limits<uint32_t>::max())));
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
PostingListFoldedSearchContextT<DataT>::fillBitVector(const ExecuteInfo & exec_info)
{
    (void) exec_info;
    fill_array_or_bitvector<false>();
}


template <typename BaseSC, typename AttrT, typename DataT>
StringPostingSearchContext<BaseSC, AttrT, DataT>::
StringPostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT &toBeSearched)
    : Parent(std::move(base_sc), useBitVector, toBeSearched)
{
    if (this->valid()) {
        if (this->isPrefix()) {
            auto comp = _enumStore.make_folded_comparator_prefix(this->queryTerm()->getTerm());
            this->lookupRange(comp, comp);
        } else if (this->isRegex()) {
            vespalib::string prefix(RegexpUtil::get_prefix(this->queryTerm()->getTerm()));
            auto comp = _enumStore.make_folded_comparator_prefix(prefix.c_str());
            this->lookupRange(comp, comp);
        } else if (this->isFuzzy()) {
            vespalib::string prefix(this->getFuzzyMatcher().getPrefix());
            auto comp = _enumStore.make_folded_comparator_prefix(prefix.c_str());
            this->lookupRange(comp, comp);
        } else {
            auto comp = _enumStore.make_folded_comparator(this->queryTerm()->getTerm());
            this->lookupTerm(comp);
        }
        if (this->_uniqueValues == 1u) {
            /*
             * A single dictionary entry from lookupRange() might not be
             * a match if this is a regex search or a fuzzy search.
             */
            if (!this->_lowerDictItr.valid() || use_single_dictionary_entry(this->_lowerDictItr)) {
                this->lookupSingle();
            } else {
                this->_uniqueValues = 0;
            }
        }
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool
StringPostingSearchContext<BaseSC, AttrT, DataT>::use_dictionary_entry(PostingListSearchContext::DictionaryConstIterator& it) const {
    if ( this->isRegex() ) {
        if (this->getRegex().valid() &&
            this->getRegex().partial_match(_enumStore.get_value(it.getKey().load_acquire()))) {
            return true;
        }
        ++it;
        return false;
    } else if ( this->isCased() ) {
        if (this->match(_enumStore.get_value(it.getKey().load_acquire()))) {
            return true;
        }
        ++it;
        return false;
    } else if (this->isFuzzy()) {
        return this->is_fuzzy_match(_enumStore.get_value(it.getKey().load_acquire()), it, _enumStore.get_data_store());
    }
    return true;
}

template <typename BaseSC, typename AttrT, typename DataT>
bool
StringPostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(const ExecuteInfo& info) const
{
    if (this->isFuzzy()) {
        uint32_t exp_doc_hits = this->_docIdLimit * info.hit_rate();
        constexpr uint32_t fuzzy_use_posting_lists_doc_limit = 10000;
        /**
         * The above constant was derived after a query latency experiment with fuzzy matching
         * on 2M documents with a dictionary size of 292070.
         *
         * Cost per document in dfa-based fuzzy matching (scanning the dictionary and merging posting lists) - strict iterator:
         *   2.8 ms / 2k = 0.0014 ms
         *   4.4 ms / 20k = 0.00022 ms
         *   9.0 ms / 200k = 0.000045 ms
         *   98 ms / 1M = 0.000098 ms
         *
         * Cost per document in lookup-based fuzzy matching - non-strict iterator:
         *   7.6 ms / 2k = 0.0038 ms
         *   54 ms / 20k = 0.0027 ms
         *   529 ms / 200k = 0.0026 ms
         *
         * Based on this experiment, we observe that we should avoid lookup-based fuzzy matching
         * when the number of documents to calculate this on exceeds a number between 2000 - 20000.
         *
         * Also note that the cost of scanning the dictionary and performing the fuzzy matching
         * is already performed at this point.
         * The only work remaining if returning true is merging the posting lists.
         */
        if (exp_doc_hits > fuzzy_use_posting_lists_doc_limit) {
            return true;
        }
    }
    return false;
}

}
