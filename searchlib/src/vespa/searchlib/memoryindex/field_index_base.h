// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "feature_store.h"
#include "field_index_remover.h"
#include "i_field_index.h"
#include "word_store.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/memoryusage.h>

namespace search::memoryindex {

class IOrderedFieldIndexInserter;

/**
 * Abstract base class for implementation of memory index for a single field.
 *
 * Contains all components that are not dependent of the posting list format.
 */
class FieldIndexBase : public IFieldIndex {
public:
    /**
     * Class representing a word used as key in the dictionary.
     */
    struct WordKey {
        vespalib::datastore::EntryRef _wordRef;

        explicit WordKey(vespalib::datastore::EntryRef wordRef) : _wordRef(wordRef) { }
        WordKey() : _wordRef() { }

        friend vespalib::asciistream&
        operator<<(vespalib::asciistream& os, const WordKey& rhs);
    };

    /**
     * Comparator class for words used in the dictionary.
     */
    class KeyComp {
    private:
        const WordStore& _wordStore;
        const vespalib::stringref _word;

        const char* getWord(vespalib::datastore::EntryRef wordRef) const {
            if (wordRef.valid()) {
                return _wordStore.getWord(wordRef);
            }
            return _word.data();
        }

    public:
        KeyComp(const WordStore& wordStore, const vespalib::stringref word)
            : _wordStore(wordStore),
              _word(word)
        { }

        bool operator()(const WordKey& lhs, const WordKey& rhs) const {
            int cmpres = strcmp(getWord(lhs._wordRef), getWord(rhs._wordRef));
            return cmpres < 0;
        }
    };

    using PostingListPtr = vespalib::datastore::AtomicEntryRef;
    using DictionaryTree = vespalib::btree::BTree<WordKey, PostingListPtr,
                                        vespalib::btree::NoAggregated,
                                        const KeyComp>;

protected:
    using GenerationHandler = vespalib::GenerationHandler;

    WordStore               _wordStore;
    uint64_t                _numUniqueWords;
    GenerationHandler       _generationHandler;
    DictionaryTree          _dict;
    FeatureStore            _featureStore;
    const uint32_t          _fieldId;
    FieldIndexRemover       _remover;
    std::unique_ptr<IOrderedFieldIndexInserter> _inserter;
    index::FieldLengthCalculator _calculator;

    void incGeneration() {
        _generationHandler.incGeneration();
    }

public:
    vespalib::datastore::EntryRef addWord(const vespalib::stringref word) {
        _numUniqueWords++;
        return _wordStore.addWord(word);
    }

    vespalib::datastore::EntryRef addFeatures(const index::DocIdAndFeatures& features) {
        return _featureStore.addFeatures(_fieldId, features).first;
    }

    void add_features_guard_bytes() { _featureStore.add_features_guard_bytes(); }

    FieldIndexBase(const index::Schema& schema, uint32_t fieldId);
    FieldIndexBase(const index::Schema& schema, uint32_t fieldId, const index::FieldLengthInfo& info);
    ~FieldIndexBase();

    uint64_t getNumUniqueWords() const override { return _numUniqueWords; }
    const FeatureStore& getFeatureStore() const override { return _featureStore; }
    const WordStore& getWordStore() const override { return _wordStore; }
    IOrderedFieldIndexInserter& getInserter() override { return *_inserter; }
    index::FieldLengthCalculator& get_calculator() override { return _calculator; }

    GenerationHandler::Guard takeGenerationGuard() override {
        return _generationHandler.takeGuard();
    }

    DictionaryTree& getDictionaryTree() { return _dict; }
    FieldIndexRemover& getDocumentRemover() override { return _remover; }

};

}

