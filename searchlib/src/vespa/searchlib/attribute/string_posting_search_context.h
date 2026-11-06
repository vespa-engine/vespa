// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_dictionary_lookup.h"
#include "posting_list_folded_search_context.h"
#include "postinglistsearchcontext.h"

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
class StringPostingSearchContext
    : public PostingSearchContext<BaseSC, PostingListFoldedSearchContextT<DataT>, AttrT> {
private:
    using ExecuteInfo = queryeval::ExecuteInfo;
    using Parent = PostingSearchContext<BaseSC, PostingListFoldedSearchContextT<DataT>, AttrT>;
    using RegexpUtil = vespalib::RegexpUtil;
    using Parent::_enumStore;
    using Parent::matcher;

    class DictionaryLookupAdapter : public IDictionaryLookup {
        StringPostingSearchContext& _sc;

    public:
        explicit DictionaryLookupAdapter(StringPostingSearchContext& sc) : _sc(sc) {}
        void lookupTerm(const vespalib::datastore::EntryComparator& comp) override { _sc.lookupTerm(comp); }
        void lookupRange(const vespalib::datastore::EntryComparator& low,
                         const vespalib::datastore::EntryComparator& high) override {
            _sc.lookupRange(low, high);
        }
        bool supports_range_limit() const override { return _sc._dictionary.get_has_btree_dictionary(); }
        void apply_range_limit(long range_limit) override { _sc.applyRangeLimit(range_limit); }
    };

    // Note: Steps iterator one or more steps when not using dictionary entry
    bool use_dictionary_entry(PostingListSearchContext::DictionaryConstIterator& it) const override;
    // Note: Uses copy of dictionary iterator to avoid stepping original.
    bool use_single_dictionary_entry(PostingListSearchContext::DictionaryConstIterator it) const {
        return use_dictionary_entry(it);
    }
    bool use_posting_lists_when_non_strict(const ExecuteInfo& info) const override;

public:
    StringPostingSearchContext(BaseSC&& base_sc, bool useBitVector, const AttrT& toBeSearched);
};

} // namespace search::attribute
