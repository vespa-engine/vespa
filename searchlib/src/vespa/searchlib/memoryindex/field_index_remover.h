// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "compact_words_store.h"
#include "i_field_index_insert_listener.h"

namespace search::memoryindex {

class IFieldIndexRemoveListener;
class WordStore;

/**
 * Class used to handle removal of documents from a FieldIndex.
 *
 * It tracks all {word, docId} tuples that are inserted into the index,
 * and when removing a document, all these {word, docId} tuples are sent to the component
 * that is doing the actual removal (IFieldIndexRemoveListener).
 */
class FieldIndexRemover : public IFieldIndexInsertListener {
private:
    struct WordFieldDocTuple {
        vespalib::datastore::EntryRef _wordRef;
        uint32_t _docId;

        WordFieldDocTuple() noexcept :
            _wordRef(0),
            _docId(0)
        { }
        WordFieldDocTuple(vespalib::datastore::EntryRef wordRef, uint32_t docId) noexcept :
            _wordRef(wordRef),
            _docId(docId)
        { }
        bool operator<(const WordFieldDocTuple &rhs) const {
            if (_docId != rhs._docId) {
                return _docId < rhs._docId;
            }
            return _wordRef < rhs._wordRef;
        }
        struct Radix {
            uint32_t operator () (const WordFieldDocTuple & wft) const {
               return wft._docId;
            }
        };
    };

    CompactWordsStore              _store;
    CompactWordsStore::Builder::UP _builder;
    std::vector<WordFieldDocTuple> _wordFieldDocTuples;
    const WordStore &_wordStore;

public:
    FieldIndexRemover(const WordStore &wordStore);
    ~FieldIndexRemover() override;
    void remove(uint32_t docId, IFieldIndexRemoveListener &inverter);
    CompactWordsStore &getStore() { return _store; }
    const CompactWordsStore &getStore() const { return _store; }

    // Implements IFieldIndexInsertListener
    void insert(vespalib::datastore::EntryRef wordRef, uint32_t docId) override;
    void flush() override;
};

}

