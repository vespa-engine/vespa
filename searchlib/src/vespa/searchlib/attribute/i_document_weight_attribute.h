// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postinglisttraits.h"
#include <functional>

namespace search::fef       { class TermFieldMatchData; }
namespace search::queryeval { class SearchIterator; }

namespace search {

using DocumentWeightIterator = attribute::PostingListTraits<int32_t>::const_iterator;

struct IDocumentWeightAttribute
{
    struct LookupKey {
        virtual ~LookupKey() = default;
        // It is required that the string is zero terminated
        virtual vespalib::stringref asString() const = 0;
        virtual bool asInteger(int64_t &value) const;
    };

    struct LookupResult {
        const vespalib::datastore::EntryRef posting_idx;
        const uint32_t posting_size;
        const int32_t min_weight;
        const int32_t max_weight;
        const vespalib::datastore::EntryRef enum_idx;
        LookupResult() : posting_idx(), posting_size(0), min_weight(0), max_weight(0), enum_idx() {}
        LookupResult(vespalib::datastore::EntryRef posting_idx_in, uint32_t posting_size_in, int32_t min_weight_in, int32_t max_weight_in, vespalib::datastore::EntryRef enum_idx_in)
            : posting_idx(posting_idx_in), posting_size(posting_size_in), min_weight(min_weight_in), max_weight(max_weight_in), enum_idx(enum_idx_in) {}
    };
    virtual vespalib::datastore::EntryRef get_dictionary_snapshot() const = 0;
    virtual LookupResult lookup(const LookupKey & key, vespalib::datastore::EntryRef dictionary_snapshot) const = 0;

    // Convenience only use by various tests.
    LookupResult lookup(vespalib::stringref term, vespalib::datastore::EntryRef dictionary_snapshot) const;
    /*
     * Collect enum indexes (via callback) where folded
     * (e.g. lowercased) value equals the folded value for enum_idx.
     */
    virtual void collect_folded(vespalib::datastore::EntryRef enum_idx, vespalib::datastore::EntryRef dictionary_snapshot, const std::function<void(vespalib::datastore::EntryRef)>& callback) const = 0;
    virtual void create(vespalib::datastore::EntryRef idx, std::vector<DocumentWeightIterator> &dst) const = 0;
    virtual DocumentWeightIterator create(vespalib::datastore::EntryRef idx) const = 0;
    virtual std::unique_ptr<queryeval::SearchIterator> make_bitvector_iterator(vespalib::datastore::EntryRef idx, uint32_t doc_id_limit, fef::TermFieldMatchData &match_data, bool strict) const = 0;
    virtual ~IDocumentWeightAttribute() = default;
};

}

