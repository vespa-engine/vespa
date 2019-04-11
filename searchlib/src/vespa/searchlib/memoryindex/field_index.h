// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "feature_store.h"
#include "field_index_remover.h"
#include "wordstore.h"
#include <vespa/searchlib/btree/btree.h>
#include <vespa/searchlib/btree/btreenodeallocator.h>
#include <vespa/searchlib/btree/btreeroot.h>
#include <vespa/searchlib/btree/btreestore.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/indexbuilder.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::memoryindex {

class OrderedFieldIndexInserter;

/**
 * Memory index for a single field.
 *
 * It consists of the following components:
 *   - WordStore containing all unique words in this field (across all documents).
 *   - B-Tree dictionary that maps from unique word (32-bit ref) -> posting list (32-bit ref).
 *   - B-Tree posting lists that maps from document id (32-bit) -> features (32-bit ref).
 *   - BTreeStore containing all the posting lists.
 *   - FeatureStore containing information on where a (word, document) pair matched this field.
 *     This information is unpacked and used during ranking.
 *
 * Elements in the three stores are accessed using 32-bit references / handles.
 */
class FieldIndex {
public:
    // Mapping from docid -> feature ref
    using PostingList = btree::BTreeRoot<uint32_t, uint32_t, search::btree::NoAggregated>;
    using PostingListStore = btree::BTreeStore<uint32_t, uint32_t,
                                               search::btree::NoAggregated,
                                               std::less<uint32_t>,
                                               btree::BTreeDefaultTraits>;
    using PostingListKeyDataType = PostingListStore::KeyDataType;

    struct WordKey {
        datastore::EntryRef _wordRef;

        explicit WordKey(datastore::EntryRef wordRef) : _wordRef(wordRef) { }
        WordKey() : _wordRef() { }

        friend vespalib::asciistream &
        operator<<(vespalib::asciistream & os, const WordKey & rhs);
    };

    class KeyComp {
    private:
        const WordStore          &_wordStore;
        const vespalib::stringref _word;

        const char *getWord(datastore::EntryRef wordRef) const {
            if (wordRef.valid()) {
                return _wordStore.getWord(wordRef);
            }
            return _word.data();
        }

    public:
        KeyComp(const WordStore &wordStore, const vespalib::stringref word)
            : _wordStore(wordStore),
              _word(word)
        { }

        bool operator()(const WordKey & lhs, const WordKey & rhs) const {
            int cmpres = strcmp(getWord(lhs._wordRef), getWord(rhs._wordRef));
            return cmpres < 0;
        }
    };

    using PostingListPtr = uint32_t;
    using DictionaryTree = btree::BTree<WordKey, PostingListPtr,
                                        search::btree::NoAggregated,
                                        const KeyComp>;
private:
    using GenerationHandler = vespalib::GenerationHandler;

    WordStore               _wordStore;
    uint64_t                _numUniqueWords;
    GenerationHandler       _generationHandler;
    DictionaryTree          _dict;
    PostingListStore        _postingListStore;
    FeatureStore            _featureStore;
    uint32_t                _fieldId;
    FieldIndexRemover       _remover;
    std::unique_ptr<OrderedFieldIndexInserter> _inserter;

public:
    datastore::EntryRef addWord(const vespalib::stringref word) {
        _numUniqueWords++;
        return _wordStore.addWord(word);
    }

    datastore::EntryRef addFeatures(const index::DocIdAndFeatures &features) {
        return _featureStore.addFeatures(_fieldId, features).first;
    }

    FieldIndex(const index::Schema &schema, uint32_t fieldId);
    ~FieldIndex();
    PostingList::Iterator find(const vespalib::stringref word) const;

    PostingList::ConstIterator
    findFrozen(const vespalib::stringref word) const;

    uint64_t getNumUniqueWords() const { return _numUniqueWords; }
    const FeatureStore & getFeatureStore() const { return _featureStore; }
    const WordStore &getWordStore() const { return _wordStore; }
    OrderedFieldIndexInserter &getInserter() const { return *_inserter; }

private:
    void freeze() {
        _postingListStore.freeze();
        _dict.getAllocator().freeze();
    }

    void trimHoldLists() {
        GenerationHandler::generation_t usedGen =
            _generationHandler.getFirstUsedGeneration();
        _postingListStore.trimHoldLists(usedGen);
        _dict.getAllocator().trimHoldLists(usedGen);
        _featureStore.trimHoldLists(usedGen);
    }

    void transferHoldLists() {
        GenerationHandler::generation_t generation =
            _generationHandler.getCurrentGeneration();
        _postingListStore.transferHoldLists(generation);
        _dict.getAllocator().transferHoldLists(generation);
        _featureStore.transferHoldLists(generation);
    }

    void incGeneration() {
        _generationHandler.incGeneration();
    }

public:
    GenerationHandler::Guard takeGenerationGuard() {
        return _generationHandler.takeGuard();
    }

    void
    compactFeatures();

    void dump(search::index::IndexBuilder & indexBuilder);

    MemoryUsage getMemoryUsage() const;
    DictionaryTree &getDictionaryTree() { return _dict; }
    PostingListStore &getPostingListStore() { return _postingListStore; }
    FieldIndexRemover &getDocumentRemover() { return _remover; }

    void commit() {
        _remover.flush();
        freeze();
        transferHoldLists();
        incGeneration();
        trimHoldLists();
    }
};

}

namespace search::btree {

extern template
class BTreeNodeDataWrap<memoryindex::FieldIndex::WordKey,
                        BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeNodeT<memoryindex::FieldIndex::WordKey,
                 BTreeDefaultTraits::INTERNAL_SLOTS>;

#if 0
extern template
class BTreeNodeT<memoryindex::FieldIndex::WordKey,
                 BTreeDefaultTraits::LEAF_SLOTS>;
#endif

extern template
class BTreeNodeTT<memoryindex::FieldIndex::WordKey,
                  datastore::EntryRef,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::INTERNAL_SLOTS>;

extern template
class BTreeNodeTT<memoryindex::FieldIndex::WordKey,
                  memoryindex::FieldIndex::PostingListPtr,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeInternalNode<memoryindex::FieldIndex::WordKey,
                        search::btree::NoAggregated,
                        BTreeDefaultTraits::INTERNAL_SLOTS>;

extern template
class BTreeLeafNode<memoryindex::FieldIndex::WordKey,
                    memoryindex::FieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeNodeStore<memoryindex::FieldIndex::WordKey,
                     memoryindex::FieldIndex::PostingListPtr,
                     search::btree::NoAggregated,
                     BTreeDefaultTraits::INTERNAL_SLOTS,
                     BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeIterator<memoryindex::FieldIndex::WordKey,
                    memoryindex::FieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    const memoryindex::FieldIndex::KeyComp,
                    BTreeDefaultTraits>;

extern template
class BTree<memoryindex::FieldIndex::WordKey,
            memoryindex::FieldIndex::PostingListPtr,
            search::btree::NoAggregated,
            const memoryindex::FieldIndex::KeyComp,
            BTreeDefaultTraits>;

extern template
class BTreeRoot<memoryindex::FieldIndex::WordKey,
               memoryindex::FieldIndex::PostingListPtr,
                search::btree::NoAggregated,
               const memoryindex::FieldIndex::KeyComp,
               BTreeDefaultTraits>;

extern template
class BTreeRootBase<memoryindex::FieldIndex::WordKey,
                    memoryindex::FieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::INTERNAL_SLOTS,
                    BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeNodeAllocator<memoryindex::FieldIndex::WordKey,
                       memoryindex::FieldIndex::PostingListPtr,
                         search::btree::NoAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS>;

}
