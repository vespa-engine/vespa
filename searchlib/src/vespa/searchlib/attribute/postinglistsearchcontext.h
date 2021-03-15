// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "postinglisttraits.h"
#include "postingstore.h"
#include "ipostinglistsearchcontext.h"
#include "posting_list_merger.h"
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchcommon/common/range.h>
#include <vespa/vespalib/util/regexp.h>
#include <regex>

namespace search::attribute {

class ISearchContext;

/**
 * Search context helper for posting list attributes, used to instantiate
 * iterators based on posting lists instead of brute force filtering search.
 */

class PostingListSearchContext : public IPostingListSearchContext
{
protected:
    using Dictionary = EnumPostingTree;
    using DictionaryConstIterator = Dictionary::ConstIterator;
    using FrozenDictionary = Dictionary::FrozenView;
    using EnumIndex = IEnumStore::Index;

    const IEnumStoreDictionary& _dictionary;
    const FrozenDictionary _frozenDictionary;
    DictionaryConstIterator _lowerDictItr;
    DictionaryConstIterator _upperDictItr;
    uint32_t                _uniqueValues;
    uint32_t                _docIdLimit;
    uint32_t                _dictSize;
    uint64_t                _numValues; // attr.getStatus().getNumValues();
    bool                    _hasWeight;
    bool                    _useBitVector;
    vespalib::datastore::EntryRef     _pidx;
    vespalib::datastore::EntryRef     _frozenRoot; // Posting list in tree form
    float _FSTC;  // Filtering Search Time Constant
    float _PLSTC; // Posting List Search Time Constant
    uint32_t                _minBvDocFreq;
    const GrowableBitVector *_gbv; // bitvector if _useBitVector has been set
    const ISearchContext    &_baseSearchCtx;


    PostingListSearchContext(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues, bool hasWeight,
                             uint32_t minBvDocFreq, bool useBitVector, const ISearchContext &baseSearchCtx);

    ~PostingListSearchContext();

    void lookupTerm(const vespalib::datastore::EntryComparator &comp);
    void lookupRange(const vespalib::datastore::EntryComparator &low, const vespalib::datastore::EntryComparator &high);
    void lookupSingle();
    virtual bool useThis(const DictionaryConstIterator & it) const {
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
        uint32_t numHits = calculateApproxNumHits();
        // numHits > 1000: make sure that posting lists are unit tested.
        return (numHits > 1000) &&
            (calculateFilteringCost() < calculatePostingListCost(numHits));
    }

public:
};


template <class DataT>
class PostingListSearchContextT : public PostingListSearchContext
{
protected:
    using DataType = DataT;
    using Traits = PostingListTraits<DataType>;
    using PostingList = typename Traits::PostingList;
    using Posting = typename Traits::Posting;
    using EntryRef = vespalib::datastore::EntryRef;
    using FrozenView = typename PostingList::BTreeType::FrozenView;

    const PostingList    &_postingList;
    /*
     * Synthetic posting lists for range search, in array or bitvector form
     */
    PostingListMerger<DataT> _merger;

    static const long MIN_UNIQUE_VALUES_BEFORE_APPROXIMATION = 100;
    static const long MIN_UNIQUE_VALUES_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION = 20;
    static const long MIN_APPROXHITS_TO_NUMDOCS_RATIO_BEFORE_APPROXIMATION = 10;

    PostingListSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                              bool hasWeight, const PostingList &postingList, uint32_t minBvDocFreq,
                              bool useBitVector, const ISearchContext &baseSearchCtx);
    ~PostingListSearchContextT() override;

    void lookupSingle();
    size_t countHits() const;
    void fillArray();
    void fillBitVector();

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
protected:
    using Parent = PostingListSearchContextT<DataT>;
    using Dictionary = typename Parent::Dictionary;
    using PostingList = typename Parent::PostingList;
    using Parent::_lowerDictItr;
    using Parent::_uniqueValues;
    using Parent::_postingList;
    using Parent::_docIdLimit;
    using Parent::countHits;
    using Parent::singleHits;

    PostingListFoldedSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                                    bool hasWeight, const PostingList &postingList, uint32_t minBvDocFreq,
                                    bool useBitVector, const ISearchContext &baseSearchCtx);

    unsigned int approximateHits() const override;
};


template <typename BaseSC, typename BaseSC2, typename AttrT>
class PostingSearchContext: public BaseSC,
                            public BaseSC2
{
public:
    using EnumStore = typename AttrT::EnumStore;
    using QueryTermSimpleUP = std::unique_ptr<QueryTermSimple>;
protected:
    const AttrT           &_toBeSearched;
    const EnumStore       &_enumStore;

    PostingSearchContext(QueryTermSimpleUP qTerm, bool useBitVector, const AttrT &toBeSearched);
    ~PostingSearchContext();
};

template <typename BaseSC, typename AttrT, typename DataT>
class StringPostingSearchContext
    : public PostingSearchContext<BaseSC, PostingListFoldedSearchContextT<DataT>, AttrT>
{
private:
    using AggregationTraits = PostingListTraits<DataT>;
    using PostingList = typename AggregationTraits::PostingList;
    using Parent = PostingSearchContext<BaseSC, PostingListFoldedSearchContextT<DataT>, AttrT>;
    using RegexpUtil = vespalib::RegexpUtil;
    using QueryTermSimpleUP = typename Parent::QueryTermSimpleUP;
    using Parent::_toBeSearched;
    using Parent::_enumStore;
    using Parent::isRegex;
    using Parent::getRegex;
    bool useThis(const PostingListSearchContext::DictionaryConstIterator & it) const override {
        return isRegex() ? (getRegex().valid() ? getRegex().partial_match(_enumStore.get_value(it.getKey())) : false ) : true;
    }
public:
    StringPostingSearchContext(QueryTermSimpleUP qTerm, bool useBitVector, const AttrT &toBeSearched);
};

template <typename BaseSC, typename AttrT, typename DataT>
class NumericPostingSearchContext
    : public PostingSearchContext<BaseSC, PostingListSearchContextT<DataT>, AttrT>
{
private:
    typedef PostingSearchContext<BaseSC, PostingListSearchContextT<DataT>, AttrT> Parent;
    typedef PostingListTraits<DataT> AggregationTraits;
    typedef typename AggregationTraits::PostingList PostingList;
    typedef typename Parent::EnumStore::ComparatorType ComparatorType;
    typedef typename AttrT::T BaseType;
    using Params = attribute::SearchContextParams;
    using QueryTermSimpleUP = typename Parent::QueryTermSimpleUP;
    using Parent::_low;
    using Parent::_high;
    using Parent::_toBeSearched;
    using Parent::_enumStore;
    Params _params;

    void getIterators(bool shouldApplyRangeLimit);
    bool valid() const override { return this->isValid(); }

    bool fallbackToFiltering() const override {
        return (this->getRangeLimit() != 0)
            ? false
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
    NumericPostingSearchContext(QueryTermSimpleUP qTerm, const Params & params, const AttrT &toBeSearched);
    const Params &params() const { return _params; }
};


template <typename BaseSC, typename BaseSC2, typename AttrT>
PostingSearchContext<BaseSC, BaseSC2, AttrT>::
PostingSearchContext(QueryTermSimpleUP qTerm, bool useBitVector, const AttrT &toBeSearched)
    : BaseSC(std::move(qTerm), toBeSearched),
      BaseSC2(toBeSearched.getEnumStore().get_dictionary(),
              toBeSearched.getCommittedDocIdLimit(),
              toBeSearched.getStatus().getNumValues(),
              toBeSearched.hasWeightedSetType(),
              toBeSearched.getPostingList(),
              toBeSearched.getPostingList()._minBvDocFreq,
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
StringPostingSearchContext(QueryTermSimpleUP qTerm, bool useBitVector, const AttrT &toBeSearched)
    : Parent(std::move(qTerm), useBitVector, toBeSearched)
{
    // after benchmarking prefix search performance on single, array, and weighted set fast-aggregate string attributes
    // with 1M values the following constant has been derived:
    this->_FSTC  = 0.000028;

    // after benchmarking prefix search performance on single, array, and weighted set fast-search string attributes
    // with 1M values the following constant has been derived:
    this->_PLSTC = 0.000000;

    if (this->valid()) {
        if (this->isPrefix()) {
            auto comp = _enumStore.make_folded_comparator(this->queryTerm()->getTerm(), true);
            this->lookupRange(comp, comp);
        } else if (this->isRegex()) {
            vespalib::string prefix(RegexpUtil::get_prefix(this->queryTerm()->getTerm()));
            auto comp = _enumStore.make_folded_comparator(prefix.c_str(), true);
            this->lookupRange(comp, comp);
        } else {
            auto comp = _enumStore.make_folded_comparator(this->queryTerm()->getTerm());
            this->lookupTerm(comp);
        }
        if (this->_uniqueValues == 1u) {
            this->lookupSingle();
        }
    }
}


template <typename BaseSC, typename AttrT, typename DataT>
NumericPostingSearchContext<BaseSC, AttrT, DataT>::
NumericPostingSearchContext(QueryTermSimpleUP qTerm, const Params & params_in, const AttrT &toBeSearched)
    : Parent(std::move(qTerm), params_in.useBitVector(), toBeSearched),
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
    if (shouldApplyRangeLimit) {
        this->applyRangeLimit(this->getRangeLimit());
    }

    if (this->_lowerDictItr != this->_upperDictItr) {
        _low = _enumStore.get_value(this->_lowerDictItr.getKey());
        auto last = this->_upperDictItr;
        --last;
        _high = _enumStore.get_value(last.getKey());
    }
}



extern template class PostingListSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListSearchContextT<int32_t>;
extern template class PostingListFoldedSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListFoldedSearchContextT<int32_t>;

}
