// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_tree_annotator.h"
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <unordered_set>

namespace search::predicate {

/**
 * Class used to track the {featureId, docId} pairs that are inserted
 * into the btree memory index dictionary. These pairs are later used
 * when removing all remains of a document from the feature posting
 * lists of the dictionary.
 */
class DocumentFeaturesStore {
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
        KeyComp(const WordStore &word_store, vespalib::stringref word)
            : _word_store(word_store),
              _word(word) {
        }

        bool operator()(const vespalib::datastore::EntryRef &lhs,
                        const vespalib::datastore::EntryRef &rhs) const {
            return strcmp(getWord(lhs), getWord(rhs)) < 0;
        }
    };
    using FeatureVector = vespalib::Array<uint64_t>;
    using DocumentFeaturesMap = vespalib::hash_map<uint32_t, FeatureVector>;
    using RangeVector = vespalib::Array<Range>;
    using RangeFeaturesMap = vespalib::hash_map<uint32_t, RangeVector>;
    using WordIndex = vespalib::btree::BTree<vespalib::datastore::EntryRef, vespalib::btree::BTreeNoLeafData,
                         vespalib::btree::NoAggregated, const KeyComp &>;

    DocumentFeaturesMap _docs;
    RangeFeaturesMap    _ranges;
    WordStore           _word_store;
    WordIndex           _word_index;
    uint32_t            _currDocId;
    FeatureVector      *_currFeatures;
    size_t              _numFeatures;
    size_t              _numRanges;
    uint32_t            _arity;

    void setCurrent(uint32_t docId, FeatureVector *features);

public:
    using FeatureSet = std::unordered_set<uint64_t>;

    DocumentFeaturesStore(uint32_t arity);
    DocumentFeaturesStore(vespalib::DataBuffer &buffer);
    ~DocumentFeaturesStore();

    void insert(uint64_t featureId, uint32_t docId);
    void insert(const PredicateTreeAnnotations &annotations, uint32_t docId);
    FeatureSet get(uint32_t docId) const;
    void remove(uint32_t docId);
    vespalib::MemoryUsage getMemoryUsage() const;

    void serialize(vespalib::DataBuffer &buffer) const;
};

}
