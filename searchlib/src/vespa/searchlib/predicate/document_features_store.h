// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_tree_annotator.h"
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.h>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <unordered_set>

namespace search::predicate {

class DocumentFeaturesStoreSaver;
class ISaver;

/**
 * Class used to track the {featureId, docId} pairs that are inserted
 * into the btree memory index dictionary. These pairs are later used
 * when removing all remains of a document from the feature posting
 * lists of the dictionary.
 */
class DocumentFeaturesStore {
    friend class DocumentFeaturesStoreSaver;
    using WordStore = memoryindex::WordStore;
    struct Range {
        vespalib::datastore::EntryRef label_ref;
        int64_t from;
        int64_t to;
    };
    // Compares EntryRefs by their corresponding word in a WordStore.
    // To find a word without knowing its EntryRef, set the word in
    // the constructor and search for an illegal EntryRef.
    class KeyComp {
        const WordStore &_word_store;
        const vespalib::string _word;

        const char *getWord(vespalib::datastore::EntryRef ref) const {
            return ref.valid() ? _word_store.getWord(ref) : _word.c_str();
        }

    public:
        KeyComp(const WordStore &word_store, std::string_view word)
            : _word_store(word_store),
              _word(word) {
        }

        bool operator()(const vespalib::datastore::EntryRef &lhs,
                        const vespalib::datastore::EntryRef &rhs) const {
            return strcmp(getWord(lhs), getWord(rhs)) < 0;
        }
    };
    using WordIndex = vespalib::btree::BTree<vespalib::datastore::EntryRef, vespalib::btree::BTreeNoLeafData,
                         vespalib::btree::NoAggregated, const KeyComp &>;
    // Array store for features
    using FeaturesRefType = vespalib::datastore::EntryRefT<19>;
    using FeaturesStoreTypeMapper = vespalib::datastore::ArrayStoreDynamicTypeMapper<uint64_t>;
    using FeaturesStore = vespalib::datastore::ArrayStore<uint64_t, FeaturesRefType, FeaturesStoreTypeMapper>;
    // Array store for ranges
    using RangesRefType = vespalib::datastore::EntryRefT<19>;
    using RangesStoreTypeMapper = vespalib::datastore::ArrayStoreDynamicTypeMapper<Range>;
    using RangesStore = vespalib::datastore::ArrayStore<Range, RangesRefType, RangesStoreTypeMapper>;
    struct Refs {
        vespalib::datastore::EntryRef _features;
        vespalib::datastore::EntryRef _ranges;
    };
    using RefsVector = std::vector<Refs, vespalib::allocator_large<Refs>>;
    using generation_t = vespalib::GenerationHandler::generation_t;

    RefsVector          _refs;
    FeaturesStore       _features;
    RangesStore         _ranges;
    WordStore           _word_store;
    WordIndex           _word_index;
    uint32_t            _arity;

    static FeaturesStoreTypeMapper make_features_store_type_mapper();
    static RangesStoreTypeMapper make_ranges_store_type_mapper();
    static vespalib::datastore::ArrayStoreConfig make_features_store_config();
    static vespalib::datastore::ArrayStoreConfig make_ranges_store_config();
public:
    using FeatureSet = std::unordered_set<uint64_t>;

    DocumentFeaturesStore(uint32_t arity);
    DocumentFeaturesStore(vespalib::DataBuffer &buffer);
    ~DocumentFeaturesStore();

    void insert(const PredicateTreeAnnotations &annotations, uint32_t docId);
    FeatureSet get(uint32_t docId) const;
    void remove(uint32_t docId);
    void commit() { }
    void reclaim_memory(generation_t oldest_used_gen);
    void assign_generation(generation_t current_gen);
    vespalib::MemoryUsage getMemoryUsage() const;

    std::unique_ptr<ISaver> make_saver() const;
};

}
