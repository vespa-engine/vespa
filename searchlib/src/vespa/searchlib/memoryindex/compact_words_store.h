// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <atomic>

namespace search::memoryindex {

/**
 * Class used to store the {wordRef, docId} tuples that are inserted into a FieldIndex and its posting lists.
 *
 * These tuples are later used when removing all remains of a document from the posting lists in that index.
 */
class CompactWordsStore {
public:
    /**
     * Builder used to collect all words (as wordRefs) for a docId in a field.
     */
    class Builder {
    public:
        using UP = std::unique_ptr<Builder>;
        using WordRefVector = vespalib::Array<vespalib::datastore::EntryRef>;

    private:
        uint32_t   _docId;
        WordRefVector _words;

    public:
        Builder(uint32_t docId_);
        ~Builder();
        Builder &insert(vespalib::datastore::EntryRef wordRef);
        uint32_t docId() const { return _docId; }
        const WordRefVector &words() const { return _words; }
    };

    /**
     * Iterator over all words (as wordRefs) for a docId in a field.
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
        vespalib::datastore::EntryRef wordRef() const { return vespalib::datastore::EntryRef(_wordRef); }
        bool hasBackingBuf() const { return _buf != nullptr; }
    };

    /**
     * Store for all unique words (as wordRefs) among all documents.
     */
    class Store {
    public:
        using DataStoreType = vespalib::datastore::DataStoreT<vespalib::datastore::EntryRefT<22>>;
        using RefType = DataStoreType::RefType;

    private:
        DataStoreType               _store;
        vespalib::datastore::BufferType<uint32_t> _type;
        const uint32_t              _typeId;

    public:
        Store();
        ~Store();
        vespalib::datastore::EntryRef insert(const Builder &builder);
        Iterator get(vespalib::datastore::EntryRef wordRef) const;
        vespalib::MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }
    };

    using DocumentWordsMap = vespalib::hash_map<uint32_t, vespalib::datastore::EntryRef>;

private:
    DocumentWordsMap _docs;
    std::atomic<size_t> _docs_used_bytes;
    std::atomic<size_t> _docs_allocated_bytes;
    Store            _wordsStore;

    void update_docs_memory_usage();

public:
    CompactWordsStore();
    ~CompactWordsStore();
    void insert(const Builder &builder);
    void remove(uint32_t docId);
    Iterator get(uint32_t docId) const;
    void commit();
    vespalib::MemoryUsage getMemoryUsage() const;
};

}
