// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/btree/btreeiterator.h>

namespace search {

namespace query { class Node; }

typedef btree::BTreeConstIterator<uint32_t, int32_t, btree::MinMaxAggregated, std::less<uint32_t>, btree::BTreeDefaultTraits> DocumentWeightIterator;

struct IDocumentWeightAttribute
{
    struct LookupResult {
        const datastore::EntryRef posting_idx;
        const uint32_t posting_size;
        const int32_t min_weight;
        const int32_t max_weight;
        LookupResult() : posting_idx(), posting_size(0), min_weight(0), max_weight(0) {}
        LookupResult(datastore::EntryRef posting_idx_in, uint32_t posting_size_in, int32_t min_weight_in, int32_t max_weight_in)
            : posting_idx(posting_idx_in), posting_size(posting_size_in), min_weight(min_weight_in), max_weight(max_weight_in) {}
    };
    virtual LookupResult lookup(const vespalib::string &term) const = 0;
    virtual void create(datastore::EntryRef idx, std::vector<DocumentWeightIterator> &dst) const = 0;
    virtual DocumentWeightIterator create(datastore::EntryRef idx) const = 0;
    virtual ~IDocumentWeightAttribute() {}
};

} // namespace search

