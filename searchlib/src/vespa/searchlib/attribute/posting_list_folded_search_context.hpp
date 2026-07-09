// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_list_folded_search_context.h"

namespace search::attribute {

template <typename DataT>
PostingListFoldedSearchContextT<DataT>::PostingListFoldedSearchContextT(const IEnumStoreDictionary& dictionary,
                                                                        uint32_t docIdLimit, uint64_t numValues,
                                                                        const PostingStore&   posting_store,
                                                                        bool                  useBitVector,
                                                                        const ISearchContext& searchContext)
    : Parent(dictionary, docIdLimit, numValues, posting_store, useBitVector, searchContext),
      _resume_scan_itr(),
      _posting_indexes() {
}

template <typename DataT> PostingListFoldedSearchContextT<DataT>::~PostingListFoldedSearchContextT() = default;

template <typename DataT> size_t PostingListFoldedSearchContextT<DataT>::calc_estimated_hits_in_range() const {
    size_t sum = 0;
    bool   overflow = false;
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
void PostingListFoldedSearchContextT<DataT>::fill_array_or_bitvector_helper(EntryRef pidx) {
    if constexpr (fill_array) {
        _merger.addToArray(PostingListTraverser<PostingStore>(_posting_store, pidx));
    } else {
        _merger.addToBitVector(PostingListTraverser<PostingStore>(_posting_store, pidx));
    }
}

template <typename DataT>
template <bool fill_array>
void PostingListFoldedSearchContextT<DataT>::fill_array_or_bitvector() {
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

template <typename DataT> void PostingListFoldedSearchContextT<DataT>::fillArray() {
    fill_array_or_bitvector<true>();
}

template <typename DataT> void PostingListFoldedSearchContextT<DataT>::fillBitVector(const ExecuteInfo& exec_info) {
    (void)exec_info;
    fill_array_or_bitvector<false>();
}

} // namespace search::attribute
