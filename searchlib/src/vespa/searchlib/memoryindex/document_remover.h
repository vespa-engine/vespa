// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "compact_document_words_store.h"
#include "i_document_insert_listener.h"

namespace search::memoryindex {

class IDocumentRemoveListener;
class WordStore;

/**
 * Class used to remove documents from the memory index dictionary.
 */
class DocumentRemover : public IDocumentInsertListener {
private:
    struct WordFieldDocTuple {
        datastore::EntryRef _wordRef;
        uint32_t _docId;
        WordFieldDocTuple() :
            _wordRef(0),
            _docId(0)
        { }
        WordFieldDocTuple(datastore::EntryRef wordRef, uint32_t docId) :
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

    CompactDocumentWordsStore              _store;
    CompactDocumentWordsStore::Builder::UP _builder;
    std::vector<WordFieldDocTuple>         _wordFieldDocTuples;
    const WordStore &_wordStore;

public:
    DocumentRemover(const WordStore &wordStore);
    ~DocumentRemover();
    void remove(uint32_t docId, IDocumentRemoveListener &inverter);
    CompactDocumentWordsStore &getStore() { return _store; }
    const CompactDocumentWordsStore &getStore() const { return _store; }

    // Implements IDocumentInsertListener
    void insert(datastore::EntryRef wordRef, uint32_t docId) override;
    void flush() override;
};

}

