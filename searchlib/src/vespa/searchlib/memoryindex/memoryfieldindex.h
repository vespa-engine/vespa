// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "featurestore.h"
#include "wordstore.h"
#include "document_remover.h"
#include <vespa/searchlib/btree/btreeroot.h>
#include <vespa/searchlib/btree/btree.h>
#include <vespa/searchlib/btree/btreenodeallocator.h>
#include <vespa/searchlib/btree/btreestore.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/indexbuilder.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::memoryindex {

class OrderedDocumentInserter;
/*
 * Memory index for a single field.
 */
class MemoryFieldIndex {
public:
    typedef btree::BTreeRoot<uint32_t, uint32_t, search::btree::NoAggregated>
    PostingList; // docid -> feature ref
    typedef btree::BTreeStore<uint32_t, uint32_t,
                              search::btree::NoAggregated,
                              std::less<uint32_t>,
            btree::BTreeDefaultTraits> PostingListStore;
    typedef PostingListStore::KeyDataType PostingListKeyDataType;


    struct WordKey {
        datastore::EntryRef _wordRef;

        explicit WordKey(datastore::EntryRef wordRef) : _wordRef(wordRef) { }
        WordKey() : _wordRef() { }

        friend vespalib::asciistream &
        operator<<(vespalib::asciistream & os, const WordKey & rhs);
    };

    class KeyComp {
    private:
        const WordStore           &_wordStore;
        const vespalib::stringref  _word;

        const char *
        getWord(datastore::EntryRef wordRef) const
        {
            if (wordRef.valid()) {
                return _wordStore.getWord(wordRef);
            }
            return _word.c_str();
        }

    public:
        KeyComp(const WordStore &wordStore, const vespalib::stringref word)
            : _wordStore(wordStore),
              _word(word)
        { }

        bool
        operator()(const WordKey & lhs, const WordKey & rhs) const
        {
            int cmpres = strcmp(getWord(lhs._wordRef), getWord(rhs._wordRef));
            return cmpres < 0;
        }
    };

    typedef uint32_t PostingListPtr;
    typedef btree::BTree<WordKey, PostingListPtr,
                         search::btree::NoAggregated,
                         const KeyComp> DictionaryTree;
private:
    typedef vespalib::GenerationHandler GenerationHandler;

    WordStore               _wordStore;
    uint64_t                _numUniqueWords;
    GenerationHandler       _generationHandler;
    DictionaryTree          _dict;
    PostingListStore        _postingListStore;
    FeatureStore            _featureStore;
    uint32_t                _fieldId;
    DocumentRemover         _remover;
    std::unique_ptr<OrderedDocumentInserter> _inserter;

public:
    datastore::EntryRef addWord(const vespalib::stringref word) {
        _numUniqueWords++;
        return _wordStore.addWord(word);
    }

    datastore::EntryRef
    addFeatures(const index::DocIdAndFeatures &features)
    {
        return _featureStore.addFeatures(_fieldId, features).first;
    }

    MemoryFieldIndex(const index::Schema &schema, uint32_t fieldId);
    ~MemoryFieldIndex();
    PostingList::Iterator find(const vespalib::stringref word) const;

    PostingList::ConstIterator
    findFrozen(const vespalib::stringref word) const;

    uint64_t getNumUniqueWords() const { return _numUniqueWords; }
    const FeatureStore & getFeatureStore() const { return _featureStore; }
    const WordStore &getWordStore() const { return _wordStore; }
    OrderedDocumentInserter &getInserter() const { return *_inserter; }

private:
    void freeze() {
        _postingListStore.freeze();
        _dict.getAllocator().freeze();
    }

    void
    trimHoldLists()
    {
        GenerationHandler::generation_t usedGen =
            _generationHandler.getFirstUsedGeneration();
        _postingListStore.trimHoldLists(usedGen);
        _dict.getAllocator().trimHoldLists(usedGen);
        _featureStore.trimHoldLists(usedGen);
    }

    void
    transferHoldLists()
    {
        GenerationHandler::generation_t generation =
            _generationHandler.getCurrentGeneration();
        _postingListStore.transferHoldLists(generation);
        _dict.getAllocator().transferHoldLists(generation);
        _featureStore.transferHoldLists(generation);
    }

    void
    incGeneration()
    {
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

    DictionaryTree &
    getDictionaryTree()
    {
        return _dict;
    }

    PostingListStore &
    getPostingListStore()
    {
        return _postingListStore;
    }

    DocumentRemover &
    getDocumentRemover()
    {
        return _remover;
    }

    void commit()
    {
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
class BTreeNodeDataWrap<memoryindex::MemoryFieldIndex::WordKey,
                        BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeNodeT<memoryindex::MemoryFieldIndex::WordKey,
                 BTreeDefaultTraits::INTERNAL_SLOTS>;

#if 0
extern template
class BTreeNodeT<memoryindex::MemoryFieldIndex::WordKey,
                 BTreeDefaultTraits::LEAF_SLOTS>;
#endif

extern template
class BTreeNodeTT<memoryindex::MemoryFieldIndex::WordKey,
                  datastore::EntryRef,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::INTERNAL_SLOTS>;

extern template
class BTreeNodeTT<memoryindex::MemoryFieldIndex::WordKey,
                  memoryindex::MemoryFieldIndex::PostingListPtr,
                  search::btree::NoAggregated,
                  BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeInternalNode<memoryindex::MemoryFieldIndex::WordKey,
                        search::btree::NoAggregated,
                        BTreeDefaultTraits::INTERNAL_SLOTS>;

extern template
class BTreeLeafNode<memoryindex::MemoryFieldIndex::WordKey,
                    memoryindex::MemoryFieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeNodeStore<memoryindex::MemoryFieldIndex::WordKey,
                     memoryindex::MemoryFieldIndex::PostingListPtr,
                     search::btree::NoAggregated,
                     BTreeDefaultTraits::INTERNAL_SLOTS,
                     BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeIterator<memoryindex::MemoryFieldIndex::WordKey,
                    memoryindex::MemoryFieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    const memoryindex::MemoryFieldIndex::KeyComp,
                    BTreeDefaultTraits>;

extern template
class BTree<memoryindex::MemoryFieldIndex::WordKey,
            memoryindex::MemoryFieldIndex::PostingListPtr,
            search::btree::NoAggregated,
            const memoryindex::MemoryFieldIndex::KeyComp,
            BTreeDefaultTraits>;

extern template
class BTreeRoot<memoryindex::MemoryFieldIndex::WordKey,
               memoryindex::MemoryFieldIndex::PostingListPtr,
                search::btree::NoAggregated,
               const memoryindex::MemoryFieldIndex::KeyComp,
               BTreeDefaultTraits>;

extern template
class BTreeRootBase<memoryindex::MemoryFieldIndex::WordKey,
                    memoryindex::MemoryFieldIndex::PostingListPtr,
                    search::btree::NoAggregated,
                    BTreeDefaultTraits::INTERNAL_SLOTS,
                    BTreeDefaultTraits::LEAF_SLOTS>;

extern template
class BTreeNodeAllocator<memoryindex::MemoryFieldIndex::WordKey,
                       memoryindex::MemoryFieldIndex::PostingListPtr,
                         search::btree::NoAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS>;

}
