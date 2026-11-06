// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_matcher.h"

#include "enumstore.h"
#include "i_dictionary_lookup.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/datastore/infinity_unique_store_string_comparator.h>

namespace search::attribute {

StringRangeMatcher::StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : _query_term(std::move(query_term)),
      _helper(_query_term ? _query_term->get_string_range_spec() : nullptr, cased) {
}

StringRangeMatcher::StringRangeMatcher(StringRangeMatcher&&) noexcept = default;

StringRangeMatcher::~StringRangeMatcher() = default;

bool StringRangeMatcher::lookup_in_dictionary(const EnumStoreT<const char*>& enum_store,
                                              IDictionaryLookup&             dict) const {
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
            dict.lookupRange(make_lower(), make_upper());
        } else if (range_spec->left) {
            dict.lookupRange(
                make_lower(),
                vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    enum_store.get_data_store()));

        } else if (range_spec->right) {
            dict.lookupRange(
                vespalib::datastore::NegativeInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    enum_store.get_data_store()),
                make_upper());
        } else {
            dict.lookupRange(
                vespalib::datastore::NegativeInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    enum_store.get_data_store()),
                vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    enum_store.get_data_store()));
        }

        // Range limit currently only supported if range excludes the empty string
        // since single-value string attributes always contain the empty string in the dictionary.
        if (range_spec->has_range_limit() && dict.supports_range_limit() && !range_spec->includes_empty_string()) {
            dict.apply_range_limit(range_spec->range_limit);
        }

        return true;
    }
    return false;
}

} // namespace search::attribute
