// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "common.h"
#include "document_features_store.h"
#include "predicate_interval_store.h"
#include "simple_index.h"
#include "predicate_interval.h"
#include <vespa/searchlib/common/bitvectorcache.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/stllike/string.h>
#include <unordered_map>

namespace search::predicate {

struct PredicateTreeAnnotations;

/**
 * PredicateIndex keeps an index of boolean constraints for use with
 * the interval algorithm. It is the central component of
 * PredicateAttribute, and PredicateBlueprint uses it to obtain
 * posting lists for matching.
 */
class PredicateIndex : public PopulateInterface {
    typedef SimpleIndex<vespalib::datastore::EntryRef> IntervalIndex;
    typedef SimpleIndex<vespalib::datastore::EntryRef> BoundsIndex;
    template <typename IntervalT>
    using FeatureMap = std::unordered_map<uint64_t, std::vector<IntervalT>>;
    using generation_t = vespalib::GenerationHandler::generation_t;
    template <typename T>
    using optional = std::optional<T>;

public:
    typedef std::unique_ptr<PredicateIndex> UP;
    typedef vespalib::GenerationHandler GenerationHandler;
    typedef vespalib::GenerationHolder GenerationHolder;
    using BTreeIterator = SimpleIndex<vespalib::datastore::EntryRef>::BTreeIterator;
    using VectorIterator = SimpleIndex<vespalib::datastore::EntryRef>::VectorIterator;
private:
    uint32_t                  _arity;
    const DocIdLimitProvider &_limit_provider;
    IntervalIndex             _interval_index;
    BoundsIndex               _bounds_index;
    PredicateIntervalStore    _interval_store;
    BTreeSet                  _zero_constraint_docs;

    DocumentFeaturesStore     _features_store;
    mutable BitVectorCache    _cache;

    template <typename IntervalT>
    void addPosting(uint64_t feature, uint32_t doc_id, vespalib::datastore::EntryRef ref);

    template <typename IntervalT>
    void indexDocumentFeatures(uint32_t doc_id, const FeatureMap<IntervalT> &interval_map);

public:
    PredicateIndex(GenerationHolder &genHolder,
                   const DocIdLimitProvider &limit_provider,
                   const SimpleIndexConfig &simple_index_config, uint32_t arity);
    // deserializes PredicateIndex from buffer.
    // The observer can be used to gain some insight into what has been added to the index..
    PredicateIndex(GenerationHolder &genHolder,
                   const DocIdLimitProvider &limit_provider,
                   const SimpleIndexConfig &simple_index_config, vespalib::DataBuffer &buffer,
                   SimpleIndexDeserializeObserver<> & observer, uint32_t version);

    ~PredicateIndex() override;
    void serialize(vespalib::DataBuffer &buffer) const;
    void onDeserializationCompleted();

    void indexEmptyDocument(uint32_t doc_id);
    void indexDocument(uint32_t doc_id, const PredicateTreeAnnotations &annotations);
    void removeDocument(uint32_t doc_id);
    void commit();
    void reclaim_memory(generation_t oldest_used_gen);
    void assign_generation(generation_t current_gen);
    vespalib::MemoryUsage getMemoryUsage() const;

    int getArity() const { return _arity; }

    const ZeroConstraintDocs getZeroConstraintDocs() const {
        return _zero_constraint_docs.getFrozenView();
    }

    const IntervalIndex &getIntervalIndex() const {
        return _interval_index;
    }

    const BoundsIndex &getBoundsIndex() const {
        return _bounds_index;
    }

    const PredicateIntervalStore &getIntervalStore() const {
        return _interval_store;
    }

    void populateIfNeeded(size_t doc_id_limit);
    BitVectorCache::KeySet lookupCachedSet(const BitVectorCache::KeyAndCountSet & keys) const;
    void computeCountVector(BitVectorCache::KeySet & keys, BitVectorCache::CountVector & v) const;

    /*
     * Adjust size of structures to have space for docId.
     */
    void adjustDocIdLimit(uint32_t docId);
    PopulateInterface::Iterator::UP lookup(uint64_t key) const override;
    // Exposed for testing
    void requireCachePopulation() const { _cache.requirePopulation(); }
};

extern template class SimpleIndex<vespalib::datastore::EntryRef>;

}
