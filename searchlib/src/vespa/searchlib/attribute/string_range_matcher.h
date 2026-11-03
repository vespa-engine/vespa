// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "string_range_search_helper.h"

#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/datastore/infinity_unique_store_string_comparator.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search {
template <class EntryT> class EnumStoreT;
} // namespace search

namespace search::attribute {

class EnumHintSearchContext;
class IDictionaryLookup;

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string range.
 */
class StringRangeMatcher {
private:
    std::unique_ptr<QueryTermSimple> _query_term;
    StringRangeSearchHelper          _helper;

public:
    StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased);
    StringRangeMatcher(StringRangeMatcher&&) noexcept;
    ~StringRangeMatcher();

    [[nodiscard]] const StringRangeSpec* get_string_range_spec() const { return _helper.get_string_range_spec(); }

    [[nodiscard]] bool isValid() const { return _helper.is_valid(); }
    [[nodiscard]] bool match(const char* src) const { return _helper.is_match(src); }
    [[nodiscard]] const QueryTermUCS4* get_query_term_ptr() const noexcept {
        return dynamic_cast<QueryTermUCS4*>(_query_term.get());
    }

    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);
    bool lookup_in_dictionary(const EnumStoreT<const char*>& enum_store, IDictionaryLookup& dict) const;

    /*
     * The methods below are needed in StringPostingSearchContext
     */
    template <typename PostingSearchContextAdapterType>
    bool setup_posting_sc(const EnumStoreT<const char*>&   enum_store,
                          PostingSearchContextAdapterType& posting_sc) const {
        auto range_spec = _helper.get_string_range_spec();
        if (range_spec) {
            // In both ends, an open (exclusive) boundary uses a less-or-equal comparator while closed
            // (inclusive) boundary uses an ordinary less-than variant.  It works like this:
            // _lowerDictItr.lower_bound(low) will call low.less(candidate, boundary) finding the first
            // entry where that call returns false:
            //  - an ordinary less comparator computes "candidate < boundary", which turns false at the
            //    first candidate >= boundary, so the walk starts inclusive of the boundary entry.
            //  - a less-or-equal comparator computes "candidate <= needs", which turns false some
            //    entries later, at the first candidate > boundary, so the walk starts strictly past the
            //    boundary entry, i.e. exclusive.
            // _upperDictItr.seekPast(high) calls high.less(boundary, candidate) - the opposite order from
            // lower_bound - finding the first entry where it turns true:
            //  - an ordinary less comparator computes "boundary < candidate", which stays false while
            //    candidate <= boundary, so the walk advances past the boundary entry too, keeping it in
            //    range (inclusive upper bound).
            //  - a less-or-equal comparator computes "boundary <= candidate", which turns true one entry
            //    earlier, at the boundary entry itself, so the walk stops there instead of past it,
            //    excluding it (exclusive upper bound).
            // With that narrowing, [_lowerDictItr, _upperDictItr> will hold exactly the matching entries.
            auto make_lower = [&] {
                const char* value = range_spec->left->c_str();
                return range_spec->left_closed ? enum_store.string_lookup_comparator(value)
                                               : enum_store.string_lookup_lteq_comparator(value);
            };
            auto make_upper = [&] {
                const char* value = range_spec->right->c_str();
                return range_spec->right_closed ? enum_store.string_lookup_comparator(value)
                                                : enum_store.string_lookup_lteq_comparator(value);
            };
            if (range_spec->left && range_spec->right) {
                posting_sc.lookupRange(make_lower(), make_upper());
            } else if (range_spec->left) {
                posting_sc.lookupRange(
                    make_lower(),
                    vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                        enum_store.get_data_store()));

            } else if (range_spec->right) {
                posting_sc.lookupRange(
                    vespalib::datastore::NegativeInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                        enum_store.get_data_store()),
                    make_upper());
            } else {
                posting_sc.lookupRange(
                    vespalib::datastore::NegativeInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                        enum_store.get_data_store()),
                    vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                        enum_store.get_data_store()));
            }

            return true;
        }
        return false;
    }

    template <typename DictionaryConstIteratorType>
    bool use_dictionary_entry(const EnumStoreT<const char*>& /*enum_store*/,
                              DictionaryConstIteratorType& /*it*/) const {
        return true;
    }

    bool use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& /*info*/, uint32_t /*docid_limit*/) const {
        return false;
    }
};

} // namespace search::attribute
