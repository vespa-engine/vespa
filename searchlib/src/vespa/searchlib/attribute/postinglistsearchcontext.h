// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "postinglisttraits.h"
#include "postingstore.h"
#include "ipostinglistsearchcontext.h"
#include "posting_list_merger.h"
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchcommon/common/range.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/util/regexp.h>
#include <regex>
#include <optional>

namespace search::attribute {

class ISearchContext;

/**
 * Search context helper for posting list attributes, used to instantiate
 * iterators based on posting lists instead of brute force filtering search.
 */

class PostingListSearchContext : public IPostingListSearchContext
{
protected:
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using Dictionary = EnumPostingTree;
    using DictionaryConstIterator = Dictionary::ConstIterator;
    using FrozenDictionary = Dictionary::FrozenView;
    using EntryRef = vespalib::datastore::EntryRef;
    using EnumIndex = IEnumStore::Index;
    static constexpr uint32_t max_posting_lists_to_count = 1000;

    const IEnumStoreDictionary&   _dictionary;
    const ISearchContext&         _baseSearchCtx;
    const BitVector*              _bv; // bitvector if _useBitVector has been set
    const FrozenDictionary        _frozenDictionary;
    DictionaryConstIterator       _lowerDictItr;
    DictionaryConstIterator       _upperDictItr;
    uint64_t                      _numValues; // attr.getStatus().getNumValues();
    uint32_t                      _uniqueValues;
    uint32_t                      _docIdLimit;
    uint32_t                      _dictSize;
    EntryRef                      _pidx;
    EntryRef                      _frozenRoot; // Posting list in tree form
    bool                          _hasWeight;
    bool                          _useBitVector;
    mutable std::optional<size_t> _estimated_hits; // Snapshot of size of posting lists in range

    PostingListSearchContext(const IEnumStoreDictionary& dictionary, bool has_btree_dictionary, uint32_t docIdLimit,
                             uint64_t numValues, bool hasWeight, bool useBitVector, const ISearchContext &baseSearchCtx);

    ~PostingListSearchContext() override;

    double avg_values_per_document() const noexcept {
        return static_cast<double>(_numValues) / static_cast<double>(_docIdLimit);
    }
    double avg_postinglist_size() const noexcept {
        return static_cast<double>(_numValues) / _dictSize;
    }

    void lookupTerm(const vespalib::datastore::EntryComparator &comp);
    void lookupRange(const vespalib::datastore::EntryComparator &low, const vespalib::datastore::EntryComparator &high);
    void lookupSingle();
    size_t estimated_hits_in_range() const;
    virtual bool use_dictionary_entry(DictionaryConstIterator& it) const {
        (void) it;
        return true;
    }
    virtual bool use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& info) const = 0;

    /**
     * Calculates the estimated number of hits when _uniqueValues >= 2,
     * by looking at the posting lists in the range [lower, upper>.
     */
    virtual size_t calc_estimated_hits_in_range() const = 0;
};


template <class DataT>
class PostingListSearchContextT : public PostingListSearchContext
{
protected:
    using DataType = DataT;
    using Traits = PostingListTraits<DataType>;
    using PostingStore = typename Traits::PostingStoreType;
    using Posting = typename Traits::Posting;
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;
    using FrozenView = typename PostingStore::BTreeType::FrozenView;
    using ExecuteInfo = queryeval::ExecuteInfo;

    const PostingStore& _posting_store;
    /*
     * Synthetic posting lists for range search, in array or bitvector form
     */
    PostingListMerger<DataT> _merger;

    PostingListSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                              bool hasWeight, const PostingStore& posting_store,
                              bool useBitVector, const ISearchContext &baseSearchCtx);
    ~PostingListSearchContextT() override;

    void lookupSingle();
    virtual void fillArray();
    virtual void fillBitVector(const ExecuteInfo &);

    void fetchPostings(const ExecuteInfo &exec, bool strict) override;
    // this will be called instead of the fetchPostings function in some cases
    void diversify(bool forward, size_t wanted_hits, const IAttributeVector &diversity_attr,
                   size_t max_per_group, size_t cutoff_groups, bool cutoff_strict);

    std::unique_ptr<queryeval::SearchIterator>
    createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) override;

    unsigned int singleHits() const;
    HitEstimate calc_hit_estimate() const override;
    void applyRangeLimit(long rangeLimit);
    struct FillPart;
};


template <class DataT>
class PostingListFoldedSearchContextT : public PostingListSearchContextT<DataT>
{
public:
    static constexpr uint32_t MAX_POSTING_INDEXES_SIZE = 10000;

protected:
    using Parent = PostingListSearchContextT<DataT>;
    using Dictionary = typename Parent::Dictionary;
    using DictionaryConstIterator = Dictionary::ConstIterator;
    using EntryRef = vespalib::datastore::EntryRef;
    using PostingStore = typename Parent::PostingStore;
    using ExecuteInfo = queryeval::ExecuteInfo;
    using Parent::_docIdLimit;
    using Parent::_lowerDictItr;
    using Parent::_merger;
    using Parent::_posting_store;
    using Parent::_uniqueValues;
    using Parent::_upperDictItr;
    using Parent::singleHits;
    using Parent::use_dictionary_entry;

    mutable DictionaryConstIterator _resume_scan_itr;
    mutable std::vector<EntryRef>   _posting_indexes;

    PostingListFoldedSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                                    bool hasWeight, const PostingStore& posting_store,
                                    bool useBitVector, const ISearchContext &baseSearchCtx);
    ~PostingListFoldedSearchContextT() override;

    size_t calc_estimated_hits_in_range() const override;
    template <bool fill_array>
    void fill_array_or_bitvector_helper(EntryRef pidx);
    template <bool fill_array>
    void fill_array_or_bitvector();
    void fillArray() override;
    void fillBitVector(const ExecuteInfo &) override;
};


template <typename BaseSC, typename BaseSC2, typename AttrT>
class PostingSearchContext: public BaseSC,
                            public BaseSC2
{
public:
    using EnumStore = typename AttrT::EnumStore;
protected:
    const AttrT           &_toBeSearched;
    const EnumStore       &_enumStore;

    PostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT &toBeSearched);
    ~PostingSearchContext();
};

template <typename BaseSC, typename AttrT, typename DataT>
class StringPostingSearchContext
    : public PostingSearchContext<BaseSC, PostingListFoldedSearchContextT<DataT>, AttrT>
{
private:
    using ExecuteInfo = queryeval::ExecuteInfo;
    using Parent = PostingSearchContext<BaseSC, PostingListFoldedSearchContextT<DataT>, AttrT>;
    using RegexpUtil = vespalib::RegexpUtil;
    using Parent::_enumStore;
    // Note: Steps iterator one or more steps when not using dictionary entry
    bool use_dictionary_entry(PostingListSearchContext::DictionaryConstIterator& it) const override;
    // Note: Uses copy of dictionary iterator to avoid stepping original.
    bool use_single_dictionary_entry(PostingListSearchContext::DictionaryConstIterator it) const {
        return use_dictionary_entry(it);
    }
    bool use_posting_lists_when_non_strict(const ExecuteInfo& info) const override;
public:
    StringPostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT &toBeSearched);
};

template <typename BaseSC, typename AttrT, typename DataT>
class NumericPostingSearchContext
    : public PostingSearchContext<BaseSC, PostingListSearchContextT<DataT>, AttrT>
{
private:
    using ExecuteInfo = queryeval::ExecuteInfo;
    using Parent = PostingSearchContext<BaseSC, PostingListSearchContextT<DataT>, AttrT>;
    using BaseType = typename AttrT::T;
    using Params = attribute::SearchContextParams;
    using Parent::_low;
    using Parent::_high;
    using Parent::_toBeSearched;
    using Parent::_enumStore;
    Params _params;

    void getIterators(bool shouldApplyRangeLimit);
    bool valid() const override { return this->isValid(); }

    HitEstimate calc_hit_estimate() const override {
        HitEstimate estimate = PostingListSearchContextT<DataT>::calc_hit_estimate();
        const unsigned int limit = std::abs(this->getRangeLimit());
        return ((limit > 0) && (limit < estimate.est_hits()))
            ? HitEstimate(limit)
            : estimate;
    }
    void fetchPostings(const ExecuteInfo & execInfo, bool strict) override {
        if (params().diversityAttribute() != nullptr) {
            bool forward = (this->getRangeLimit() > 0);
            size_t wanted_hits = std::abs(this->getRangeLimit());
            PostingListSearchContextT<DataT>::diversify(forward, wanted_hits,
                                                        *(params().diversityAttribute()), this->getMaxPerGroup(),
                                                        params().diversityCutoffGroups(), params().diversityCutoffStrict());
        } else {
            PostingListSearchContextT<DataT>::fetchPostings(execInfo, strict);
        }
    }

    bool use_posting_lists_when_non_strict(const ExecuteInfo& info) const override;
    size_t calc_estimated_hits_in_range() const override;

public:
    NumericPostingSearchContext(BaseSC&& base_sc, const Params & params, const AttrT &toBeSearched);
    const Params &params() const { return _params; }
};


template <typename BaseSC, typename BaseSC2, typename AttrT>
PostingSearchContext<BaseSC, BaseSC2, AttrT>::
PostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT &toBeSearched)
    : BaseSC(std::move(base_sc)),
      BaseSC2(toBeSearched.getEnumStore().get_dictionary(),
              toBeSearched.getCommittedDocIdLimit(),
              toBeSearched.getStatus().getNumValues(),
              toBeSearched.hasWeightedSetType(),
              toBeSearched.get_posting_store(),
              useBitVector,
              *this),
      _toBeSearched(toBeSearched),
      _enumStore(_toBeSearched.getEnumStore())
{
    this->_plsc = static_cast<attribute::IPostingListSearchContext *>(this);
}

template <typename BaseSC, typename BaseSC2, typename AttrT>
PostingSearchContext<BaseSC, BaseSC2, AttrT>::~PostingSearchContext() = default;

template <typename BaseSC, typename AttrT, typename DataT>
NumericPostingSearchContext<BaseSC, AttrT, DataT>::
NumericPostingSearchContext(BaseSC&& base_sc, const Params & params_in, const AttrT &toBeSearched)
    : Parent(std::move(base_sc), params_in.useBitVector(), toBeSearched),
      _params(params_in)
{
    if (valid()) {
        if (_low == _high) {
            auto comp = _enumStore.make_comparator(_low);
            this->lookupTerm(comp);
        } else if (_low < _high) {
            bool shouldApplyRangeLimit = (params().diversityAttribute() == nullptr) &&
                                         (this->getRangeLimit() != 0);
            getIterators( shouldApplyRangeLimit );
        }
        if (this->_uniqueValues == 1u) {
            this->lookupSingle();
        }
    }
}


template <typename BaseSC, typename AttrT, typename DataT>
void
NumericPostingSearchContext<BaseSC, AttrT, DataT>::
getIterators(bool shouldApplyRangeLimit)
{
    bool isFloat =
        _toBeSearched.getBasicType() == BasicType::FLOAT ||
        _toBeSearched.getBasicType() == BasicType::DOUBLE;
    search::Range<BaseType> capped = this->template cappedRange<BaseType>(isFloat);

    auto compLow = _enumStore.make_comparator(capped.lower());
    auto compHigh = _enumStore.make_comparator(capped.upper());

    this->lookupRange(compLow, compHigh);
    if (!this->_dictionary.get_has_btree_dictionary()) {
        _low = capped.lower();
        _high = capped.upper();
        return;
    }
    if (shouldApplyRangeLimit) {
        this->applyRangeLimit(this->getRangeLimit());
    }

    if (this->_lowerDictItr != this->_upperDictItr) {
        _low = _enumStore.get_value(this->_lowerDictItr.getKey().load_acquire());
        auto last = this->_upperDictItr;
        --last;
        _high = _enumStore.get_value(last.getKey().load_acquire());
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool
NumericPostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& info) const
{
    // The following constants are derived after running parts of
    // the range search performance test with 10M documents on an Apple M1 Pro with 32 GB memory.
    // This code was compiled with two different behaviors:
    //   1) 'lookup matching' (never use posting lists).
    //   2) 'posting list matching' (always use posting lists).
    // https://github.com/vespa-engine/system-test/tree/master/tests/performance/range_search
    //
    // The following test cases were used:
    // range_hits_ratio=[100, 200], values_in_range=100, fast_search=true, filter_hits_ratio=[1, 2, 4, 6, 8, 10, 20, 40, 60, 80, 100, 120, 140, 160, 200].
    //
    // By comparing the avg query latency between 1) 'lookup matching' and 2) 'posting list matching'
    // we find the crossover point between the two strategies.
    //
    // Excerpt of results for range_hits_ratio=[100] and filter_hits_ratio=[10, 20, 40, 60]:
    //   1) 'lookup matching':       [7.1, 8.4, 14.1, 17.6]
    //   2) 'posting list matching': [7.3, 8.8, 7.4, 8.1]
    // With filter_hits_ratio=20, lookup matching is best.
    // With filter_hits_ratio=40, posting list matching is best.
    //
    // The extra cost and difference between the two strategies is modelled as follows:
    //   1) lookup matching: exp_doc_hits * lookup_match_constant (LMC)
    //   2) posting list matching: estimated_hits_in_range() * posting_list_merge_constant (PLMC)
    //
    // At the crossover point (filter_hits_ratio=20) the following costs are calculated:
    //   1) 10M*20/100 * LMC = 200k * LMC
    //   2) 10M*100/1000 * PLMC = 1M * PLMC
    //
    // Based on this we see that LMC = 5 * PLMC.
    // The same relationship is found with the test case range_hits_ratio=[200].

    constexpr double lookup_match_constant = 5.0;
    constexpr double posting_list_merge_constant = 1.0;

    uint32_t exp_doc_hits = this->_docIdLimit * info.hit_rate();
    float lookup_match_cost = exp_doc_hits * this->avg_values_per_document() * lookup_match_constant;
    float posting_list_cost = this->estimated_hits_in_range() * posting_list_merge_constant;
    return posting_list_cost < lookup_match_cost;
}

template <typename BaseSC, typename AttrT, typename DataT>
size_t
NumericPostingSearchContext<BaseSC, AttrT, DataT>::calc_estimated_hits_in_range() const
{
    size_t exact_sum = 0;
    size_t estimated_sum = 0;

    // Sample lower range
    auto it_forward = this->_lowerDictItr;
    for (uint32_t count = 0; (it_forward != this->_upperDictItr) && (count < this->max_posting_lists_to_count); ++it_forward, ++count) {
        exact_sum += this->_posting_store.frozenSize(it_forward.getData().load_acquire());
    }
    if (it_forward != this->_upperDictItr) {
        //Sample upper range
        auto it_backward = this->_upperDictItr;
        for (uint32_t count = 0; (it_backward != it_forward) && (count < this->max_posting_lists_to_count);++count) {
            --it_backward;
            exact_sum += this->_posting_store.frozenSize(it_backward.getData().load_acquire());
        }
        if (it_forward != it_backward) {
            // Estimate the rest
            uint32_t remaining_posting_lists = it_backward - it_forward;
            double measured_hits_per_posting_list = static_cast<double>(exact_sum) / (this->max_posting_lists_to_count * 2);
            // Let measure and global rate count equally, to reduce the effect of outlayers.
            estimated_sum = remaining_posting_lists * (measured_hits_per_posting_list + this->avg_postinglist_size())/2;
        }
    }
    return exact_sum + estimated_sum;
}

extern template class PostingListSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListSearchContextT<int32_t>;
extern template class PostingListFoldedSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListFoldedSearchContextT<int32_t>;

}
