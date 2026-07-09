// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postinglistsearchcontext.h"

namespace search::attribute {

template <class DataT> class PostingListFoldedSearchContextT : public PostingListSearchContextT<DataT> {
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
                                    const PostingStore& posting_store, bool useBitVector,
                                    const ISearchContext& baseSearchCtx);
    ~PostingListFoldedSearchContextT() override;

    size_t calc_estimated_hits_in_range() const override;
    template <bool fill_array> void fill_array_or_bitvector_helper(EntryRef pidx);
    template <bool fill_array> void fill_array_or_bitvector();
    void fillArray() override;
    void fillBitVector(const ExecuteInfo&) override;
};

extern template class PostingListFoldedSearchContextT<vespalib::btree::BTreeNoLeafData>;
extern template class PostingListFoldedSearchContextT<int32_t>;

} // namespace search::attribute
