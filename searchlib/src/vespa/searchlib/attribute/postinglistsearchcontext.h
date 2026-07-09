// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "ipostinglistsearchcontext.h"
#include "posting_list_merger.h"
#include "postinglisttraits.h"
#include "postingstore.h"

#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchcommon/common/range.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/util/regexp.h>

#include <optional>
#include <regex>

namespace search::attribute {

class ISearchContext;

/**
 * Search context helper for posting list attributes, used to instantiate
 * iterators based on posting lists instead of brute force filtering search.
 */

class PostingListSearchContext : public IPostingListSearchContext {
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
    bool                          _useBitVector;
    mutable std::optional<size_t> _estimated_hits;  // Snapshot of size of posting lists in range
    static bool                   _preserve_weight; // Use temporary posting list with weight information

    PostingListSearchContext(const IEnumStoreDictionary& dictionary, bool has_btree_dictionary, uint32_t docIdLimit,
                             uint64_t numValues, bool useBitVector, const ISearchContext& baseSearchCtx);

    ~PostingListSearchContext() override;

    double avg_values_per_document() const noexcept {
        return static_cast<double>(_numValues) / static_cast<double>(_docIdLimit);
    }
    double avg_postinglist_size() const noexcept { return static_cast<double>(_numValues) / _dictSize; }

    void lookupTerm(const vespalib::datastore::EntryComparator& comp);
    void lookupRange(const vespalib::datastore::EntryComparator& low,
                     const vespalib::datastore::EntryComparator& high);
    void lookupSingle();
    size_t estimated_hits_in_range() const;
    virtual bool use_dictionary_entry(DictionaryConstIterator& it) const {
        (void)it;
        return true;
    }
    virtual bool use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& info) const = 0;

    /**
     * Calculates the estimated number of hits when _uniqueValues >= 2,
     * by looking at the posting lists in the range [lower, upper>.
     */
    virtual size_t calc_estimated_hits_in_range() const = 0;

public:
    // Used by unit tests.
    static bool get_preserve_weight() noexcept { return _preserve_weight; }
    static void set_preserve_weight(bool value) noexcept { _preserve_weight = value; }
};

template <class DataT> class PostingListSearchContextT : public PostingListSearchContext {
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

    static constexpr bool merged_array_has_weight = !std::is_same_v<DataT, vespalib::btree::BTreeNoLeafData>;

    PostingListSearchContextT(const IEnumStoreDictionary& dictionary, uint32_t docIdLimit, uint64_t numValues,
                              const PostingStore& posting_store, bool useBitVector,
                              const ISearchContext& baseSearchCtx);
    ~PostingListSearchContextT() override;

    void lookupSingle();
    virtual void fillArray();
    virtual void fillBitVector(const ExecuteInfo&);

    void fetchPostings(const ExecuteInfo& exec, bool strict) override;
    // this will be called instead of the fetchPostings function in some cases
    void diversify(bool forward, size_t wanted_hits, const IAttributeVector& diversity_attr, size_t max_per_group,
                   size_t cutoff_groups, bool cutoff_strict);

    std::unique_ptr<queryeval::SearchIterator> createPostingIterator(fef::TermFieldMatchData* matchData,
                                                                     bool                     strict) override;

    unsigned int singleHits() const;
    HitEstimate calc_hit_estimate() const override;
    double posting_list_merge_factor() const override;
    void applyRangeLimit(long rangeLimit);
    struct FillPart;
};

template <typename BaseSC, typename BaseSC2, typename AttrT>
class PostingSearchContext : public BaseSC, public BaseSC2 {
public:
    using EnumStore = typename AttrT::EnumStore;

protected:
    const AttrT&     _toBeSearched;
    const EnumStore& _enumStore;

    PostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT& toBeSearched);
    ~PostingSearchContext();
};

template <typename BaseSC, typename BaseSC2, typename AttrT>
PostingSearchContext<BaseSC, BaseSC2, AttrT>::PostingSearchContext(BaseSC&& base_sc, bool useBitVector,
                                                                   const AttrT& toBeSearched)
    : BaseSC(std::move(base_sc)),
      BaseSC2(toBeSearched.getEnumStore().get_dictionary(), toBeSearched.getCommittedDocIdLimit(),
              toBeSearched.getStatus().getNumValues(), toBeSearched.get_posting_store(), useBitVector, *this),
      _toBeSearched(toBeSearched),
      _enumStore(_toBeSearched.getEnumStore()) {
    this->_plsc = static_cast<attribute::IPostingListSearchContext*>(this);
}

template <typename BaseSC, typename BaseSC2, typename AttrT>
PostingSearchContext<BaseSC, BaseSC2, AttrT>::~PostingSearchContext() = default;

extern template class PostingListSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListSearchContextT<int32_t>;

} // namespace search::attribute
