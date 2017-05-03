// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "predicate_search.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/datastore/entryref.h>
#include <vector>
#include <memory>

namespace search {
namespace query { class PredicateQuery; }

namespace queryeval {
/**
 * Blueprint for building predicate searches. It builds search
 * iterators based on PredicateSearch.
 */
class PredicateBlueprint : public ComplexLeafBlueprint {
public:
    struct IntervalEntry {
        datastore::EntryRef entry_ref;
        uint64_t subquery;
        size_t   size;
        uint64_t feature;
    };
    struct BoundsEntry {
        datastore::EntryRef entry_ref;
        uint32_t value_diff;
        uint64_t subquery;
        size_t   size;
        uint64_t feature;
    };
    template<typename I>
    struct IntervalIteratorEntry {
        I iterator;
        const IntervalEntry &entry;
    };
    template<typename I>
    struct BoundsIteratorEntry {
        I iterator;
        const BoundsEntry &entry;
    };

    PredicateBlueprint(const FieldSpecBase &field,
                       const PredicateAttribute & attribute,
                       const query::PredicateQuery &query);

    ~PredicateBlueprint();
    void fetchPostings(bool strict) override;

    SearchIterator::UP
    createLeafSearch(const fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const override;
private:
    using BTreeIterator = predicate::SimpleIndex<datastore::EntryRef>::BTreeIterator;
    using VectorIterator = predicate::SimpleIndex<datastore::EntryRef>::VectorIterator;
    template <typename T>
    using optional = std::experimental::optional<T>;
    using Alloc = vespalib::alloc::Alloc;

    const PredicateAttribute & predicate_attribute() const {
        return _attribute;
    }
    PredicateAttribute & predicate_attribute() {
        return const_cast<PredicateAttribute &>(_attribute);
    }
    void addBoundsPostingToK(uint64_t feature);
    void addPostingToK(uint64_t feature);
    void addZeroConstraintToK();
    std::vector<predicate::PredicatePostingList::UP> createPostingLists() const;

    const PredicateAttribute & _attribute;
    const predicate::PredicateIndex &_index;
    Alloc _kVBacking;
    BitVectorCache::CountVector _kV;
    BitVectorCache::KeySet _cachedFeatures;

    std::vector<IntervalEntry> _interval_dict_entries;
    std::vector<BoundsEntry> _bounds_dict_entries;
    datastore::EntryRef _zstar_dict_entry;

    std::vector<IntervalIteratorEntry<BTreeIterator>> _interval_btree_iterators;
    std::vector<IntervalIteratorEntry<VectorIterator>> _interval_vector_iterators;
    std::vector<BoundsIteratorEntry<BTreeIterator>> _bounds_btree_iterators;
    std::vector<BoundsIteratorEntry<VectorIterator>> _bounds_vector_iterators;
    // The zstar iterator is either a vector or a btree iterator.
    optional<BTreeIterator> _zstar_btree_iterator;
    optional<VectorIterator> _zstar_vector_iterator;
};

}  // namespace search::queryeval
}  // namespace search

