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

    static constexpr long MIN_UNIQUE_VALUES_BEFORE_APPROXIMATION = 100;
    static constexpr long MIN_UNIQUE_VALUES_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION = 20;
    static constexpr long MIN_APPROXHITS_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION = 10;

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
    float                         _FSTC;  // Filtering Search Time Constant
    float                         _PLSTC; // Posting List Search Time Constant
    bool                          _hasWeight;
    bool                          _useBitVector;
    mutable std::optional<size_t> _counted_hits; // Snapshot of size of posting lists in range

    PostingListSearchContext(const IEnumStoreDictionary& dictionary, bool has_btree_dictionary, uint32_t docIdLimit,
                             uint64_t numValues, bool hasWeight, bool useBitVector, const ISearchContext &baseSearchCtx);

    ~PostingListSearchContext() override;

    void lookupTerm(const vespalib::datastore::EntryComparator &comp);
    void lookupRange(const vespalib::datastore::EntryComparator &low, const vespalib::datastore::EntryComparator &high);
    void lookupSingle();
    virtual bool use_dictionary_entry(DictionaryConstIterator& it) const {
        (void) it;
        return true;
    }

    float calculateFilteringCost() const {
        // filtering search time (ms) ~ FSTC * numValues; (FSTC =
        // Filtering Search Time Constant)
        return _FSTC * _numValues;
    }

    float calculatePostingListCost(uint32_t approxNumHits) const {
        // search time (ms) ~ PLSTC * numHits * log(numHits); (PLSTC =
        // Posting List Search Time Constant)
        return _PLSTC * approxNumHits;
    }

    uint32_t calculateApproxNumHits() const {
        float docsPerUniqueValue = static_cast<float>(_docIdLimit) /
                                   static_cast<float>(_dictSize);
        return static_cast<uint32_t>(docsPerUniqueValue * _uniqueValues);
    }

    virtual bool fallbackToFiltering() const {
        if (_uniqueValues >= 2 && !_dictionary.get_has_btree_dictionary()) {
            return true; // force filtering for range search
        }
        uint32_t numHits = calculateApproxNumHits();
        // numHits > 1000: make sure that posting lists are unit tested.
        return (numHits > 1000) &&
            (calculateFilteringCost() < calculatePostingListCost(numHits));
    }
    virtual bool use_posting_list_when_non_strict(const queryeval::ExecuteInfo&) const {
        return false;
    }
    virtual bool fallback_to_approx_num_hits() const {
        return ((_uniqueValues > MIN_UNIQUE_VALUES_BEFORE_APPROXIMATION) &&
                ((_uniqueValues * MIN_UNIQUE_VALUES_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION > static_cast<int>(_docIdLimit)) ||
                 (calculateApproxNumHits() * MIN_APPROXHITS_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION > _docIdLimit) ||
                 (_uniqueValues > MIN_UNIQUE_VALUES_BEFORE_APPROXIMATION*10)));
    }
    virtual size_t countHits() const = 0;
    virtual void fillArray() = 0;
    virtual void fillBitVector() = 0;
};


template <class DataT>
class PostingListSearchContextT : public PostingListSearchContext
{
protected:
    using DataType = DataT;
    using Traits = PostingListTraits<DataType>;
    using PostingList = typename Traits::PostingList;
    using Posting = typename Traits::Posting;
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;
    using FrozenView = typename PostingList::BTreeType::FrozenView;

    const PostingList    &_postingList;
    /*
     * Synthetic posting lists for range search, in array or bitvector form
     */
    PostingListMerger<DataT> _merger;

    PostingListSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                              bool hasWeight, const PostingList &postingList,
                              bool useBitVector, const ISearchContext &baseSearchCtx);
    ~PostingListSearchContextT() override;

    void lookupSingle();
    size_t countHits() const override;
    void fillArray() override;
    void fillBitVector() override;

    void fetchPostings(const queryeval::ExecuteInfo & strict) override;
    // this will be called instead of the fetchPostings function in some cases
    void diversify(bool forward, size_t wanted_hits, const IAttributeVector &diversity_attr,
                   size_t max_per_group, size_t cutoff_groups, bool cutoff_strict);

    std::unique_ptr<queryeval::SearchIterator>
    createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) override;

    unsigned int singleHits() const;
    unsigned int approximateHits() const override;
    void applyRangeLimit(int rangeLimit);
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
    using PostingList = typename Parent::PostingList;
    using Parent::_counted_hits;
    using Parent::_docIdLimit;
    using Parent::_lowerDictItr;
    using Parent::_merger;
    using Parent::_postingList;
    using Parent::_uniqueValues;
    using Parent::_upperDictItr;
    using Parent::singleHits;
    using Parent::use_dictionary_entry;

    mutable DictionaryConstIterator _resume_scan_itr;
    mutable std::vector<EntryRef>   _posting_indexes;

    PostingListFoldedSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                                    bool hasWeight, const PostingList &postingList,
                                    bool useBitVector, const ISearchContext &baseSearchCtx);
    ~PostingListFoldedSearchContextT() override;

    bool fallback_to_approx_num_hits() const override;
    size_t countHits() const override;
    template <bool fill_array>
    void fill_array_or_bitvector_helper(EntryRef pidx);
    template <bool fill_array>
    void fill_array_or_bitvector();
    void fillArray() override;
    void fillBitVector() override;
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
    using Parent = PostingSearchContext<BaseSC, PostingListFoldedSearchContextT<DataT>, AttrT>;
    using RegexpUtil = vespalib::RegexpUtil;
    using Parent::_enumStore;
    // Note: steps iterator one ore more steps when not using dictionary entry
    bool use_dictionary_entry(PostingListSearchContext::DictionaryConstIterator& it) const override;
    // Note: Uses copy of dictionary iterator to avoid stepping original.
    bool use_single_dictionary_entry(PostingListSearchContext::DictionaryConstIterator it) const {
        return use_dictionary_entry(it);
    }
    bool use_posting_list_when_non_strict(const queryeval::ExecuteInfo&) const override;
public:
    StringPostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT &toBeSearched);
};

template <typename BaseSC, typename AttrT, typename DataT>
class NumericPostingSearchContext
    : public PostingSearchContext<BaseSC, PostingListSearchContextT<DataT>, AttrT>
{
private:
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

    bool fallbackToFiltering() const override {
        return (this->getRangeLimit() != 0)
            ? (this->_uniqueValues >= 2 && !this->_dictionary.get_has_btree_dictionary())
            : Parent::fallbackToFiltering();
    }
    unsigned int approximateHits() const override {
        const unsigned int estimate = PostingListSearchContextT<DataT>::approximateHits();
        const unsigned int limit = std::abs(this->getRangeLimit());
        return ((limit > 0) && (limit < estimate))
            ? limit
            : estimate;
    }
    void fetchPostings(const queryeval::ExecuteInfo & execInfo) override {
        if (params().diversityAttribute() != nullptr) {
            bool forward = (this->getRangeLimit() > 0);
            size_t wanted_hits = std::abs(this->getRangeLimit());
            PostingListSearchContextT<DataT>::diversify(forward, wanted_hits,
                                                        *(params().diversityAttribute()), this->getMaxPerGroup(),
                                                        params().diversityCutoffGroups(), params().diversityCutoffStrict());
        } else {
            PostingListSearchContextT<DataT>::fetchPostings(execInfo);
        }
    }

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
              toBeSearched.getPostingList(),
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
StringPostingSearchContext<BaseSC, AttrT, DataT>::
StringPostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT &toBeSearched)
    : Parent(std::move(base_sc), useBitVector, toBeSearched)
{
    // after benchmarking prefix search performance on single, array, and weighted set fast-aggregate string attributes
    // with 1M values the following constant has been derived:
    this->_FSTC  = 0.000028;

    // after benchmarking prefix search performance on single, array, and weighted set fast-search string attributes
    // with 1M values the following constant has been derived:
    this->_PLSTC = 0.000000;

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
StringPostingSearchContext<BaseSC, AttrT, DataT>::use_posting_list_when_non_strict(const queryeval::ExecuteInfo& info) const
{
    if (this->isFuzzy()) {
        uint32_t exp_doc_hits = this->_docIdLimit * info.hitRate();
        constexpr uint32_t fuzzy_use_posting_list_doc_limit = 10000;
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
        if (exp_doc_hits > fuzzy_use_posting_list_doc_limit) {
            return true;
        }
    }
    return false;
}

template <typename BaseSC, typename AttrT, typename DataT>
NumericPostingSearchContext<BaseSC, AttrT, DataT>::
NumericPostingSearchContext(BaseSC&& base_sc, const Params & params_in, const AttrT &toBeSearched)
    : Parent(std::move(base_sc), params_in.useBitVector(), toBeSearched),
      _params(params_in)
{
    // after simplyfying the formula and simple benchmarking and thumbs in the air
    // a ratio of 8 between numvalues and estimated number of hits has been found.
    this->_FSTC = 1;

    this->_PLSTC = 8;
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



extern template class PostingListSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListSearchContextT<int32_t>;
extern template class PostingListFoldedSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListFoldedSearchContextT<int32_t>;

}
