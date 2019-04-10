// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/datastore/datastore.h>
#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace search::memoryindex {

/**
 * Class used to store the {wordRef, fieldId, docId} tuples that are inserted
 * into the memory index dictionary. These tuples are later used when removing
 * all remains of a document from the posting lists of the dictionary.
 */
class CompactDocumentWordsStore {
public:
    /**
     * Builder used to collect all wordRefs for a field.
     */
    class Builder {
    public:
        using UP = std::unique_ptr<Builder>;
        using WordRefVector = vespalib::Array<datastore::EntryRef>;

    private:
        uint32_t   _docId;
        WordRefVector _words;

    public:
        Builder(uint32_t docId_);
        ~Builder();
        Builder &insert(datastore::EntryRef wordRef);
        uint32_t docId() const { return _docId; }
        const WordRefVector &words() const { return _words; }
    };

    /**
     * Iterator over all {wordRef, fieldId} pairs for a document.
     */
    class Iterator {
    private:
        const uint32_t *_buf;
        uint32_t        _remainingWords;
        uint32_t        _wordRef;
        bool            _valid;

        inline void nextWord();

    public:
        Iterator();
        Iterator(const uint32_t *buf);
        bool valid() const { return _valid; }
        Iterator &operator++();
        datastore::EntryRef wordRef() const { return datastore::EntryRef(_wordRef); }
        bool hasBackingBuf() const { return _buf != nullptr; }
    };

    /**
     * Store for all {wordRef, fieldId} pairs among all documents.
     */
    class Store {
    public:
        using DataStoreType = datastore::DataStoreT<datastore::EntryRefT<22>>;
        using RefType = DataStoreType::RefType;

    private:
        DataStoreType               _store;
        datastore::BufferType<uint32_t> _type;
        const uint32_t              _typeId;

    public:
        Store();
        ~Store();
        datastore::EntryRef insert(const Builder &builder);
        Iterator get(datastore::EntryRef ref) const;
        MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }
    };

    using DocumentWordsMap = vespalib::hash_map<uint32_t, datastore::EntryRef>;

private:
    DocumentWordsMap _docs;
    Store            _wordsStore;

public:
    CompactDocumentWordsStore();
    ~CompactDocumentWordsStore();
    void insert(const Builder &builder);
    void remove(uint32_t docId);
    Iterator get(uint32_t docId) const;
    MemoryUsage getMemoryUsage() const;
};

}
