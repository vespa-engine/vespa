// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/hash_set.h>

namespace search::index { class IndexBuilder; }

namespace search { class ISequencedTaskExecutor; }

namespace document { class Document; }

namespace search::memoryindex {

class DocumentInverter;
class Dictionary;

/**
 * Lock-free implementation of a memory-based index
 * using the document inverter and dictionary classes from searchlib.
 **/
class MemoryIndex : public queryeval::Searchable
{
private:
    index::Schema     _schema;
    ISequencedTaskExecutor &_invertThreads;
    ISequencedTaskExecutor &_pushThreads;
    std::unique_ptr<DocumentInverter>  _inverter0;
    std::unique_ptr<DocumentInverter>  _inverter1;
    DocumentInverter                  *_inverter;
    std::unique_ptr<Dictionary>        _dictionary;
    bool              _frozen;
    uint32_t          _maxDocId;
    uint32_t          _numDocs;
    vespalib::Lock    _lock;
    std::vector<bool> _hiddenFields;
    index::Schema::SP _prunedSchema;
    vespalib::hash_set<uint32_t> _indexedDocs; // documents in memory index
    const uint64_t    _staticMemoryFootprint;

    MemoryIndex(const MemoryIndex &) = delete;
    MemoryIndex(MemoryIndex &&) = delete;
    MemoryIndex &operator=(const MemoryIndex &) = delete;
    MemoryIndex &operator=(MemoryIndex &&) = delete;

    void removeDocumentHelper(uint32_t docId, const document::Document &doc);
    void updateMaxDocId(uint32_t docId) {
        if (docId > _maxDocId) {
            _maxDocId = docId;
        }
    }
    void incNumDocs() {
        ++_numDocs;
    }
    void decNumDocs() {
        if (_numDocs > 0) {
            --_numDocs;
        }
    }

    void flipInverter();

public:
    /**
     * Convenience type defs.
     */
    typedef std::unique_ptr<MemoryIndex> UP;
    typedef std::shared_ptr<MemoryIndex> SP;

    /**
     * Create a new memory index based on the given schema.
     *
     * @param schema the index schema to use
     **/
    MemoryIndex(const index::Schema &schema,
                ISequencedTaskExecutor &invertThreads,
                ISequencedTaskExecutor &pushThreads);

    /**
     * Class destructor.  Clean up washlist.
     */
    ~MemoryIndex();

    /**
     * Obtain the schema used by this index.
     *
     * @return schema used by this index
     **/
    const index::Schema &getSchema() const { return _schema; }

    /**
     * Check if this index is frozen.
     *
     * @return true if this index is frozen
     **/
    bool isFrozen() const { return _frozen; }

    /**
     * Insert a document into the index. If the document is already in
     * the index, the old version will be removed first.
     *
     * @param docId local document id.
     * @param doc the document to insert.
     **/
    void insertDocument(uint32_t docId, const document::Document &doc);

    /**
     * Remove a document from the index.
     *
     * @param docId local document id.
     **/
    void removeDocument(uint32_t docId);

    /**
     * Commits the inserts and removes since the last commit, making
     * them searchable. When commit is completed, onWriteDone goes out
     * of scope, scheduling completion callback.
     *
     * Callers can call pushThreads.sync() to wait for push completion.
     **/
    void commit(const std::shared_ptr<IDestructorCallback> &onWriteDone);

    /**
     * Freeze this index. Further index updates will be
     * discarded. Extra information kept to wash the posting lists
     * will be discarded.
     **/
    void freeze();

    /**
     * Dump the contents of this index into the given index builder.
     *
     * @param indexBuilder the builder to dump into
     **/
    void dump(index::IndexBuilder &indexBuilder);

    // implements Searchable
    queryeval::Blueprint::UP
    createBlueprint(const queryeval::IRequestContext & requestContext,
                    const queryeval::FieldSpec &field,
                    const query::Node &term) override;

    queryeval::Blueprint::UP
    createBlueprint(const queryeval::IRequestContext & requestContext,
                    const queryeval::FieldSpecList &fields,
                    const query::Node &term) override {
        return queryeval::Searchable::createBlueprint(requestContext, fields, term);
    }

    virtual uint32_t getDocIdLimit() const {
        // Used to get docId range.
        return _maxDocId + 1;
    }

    virtual uint32_t getNumDocs() const {
        return _numDocs;
    }

    virtual uint64_t getNumWords() const;

    void pruneRemovedFields(const index::Schema &schema);

    index::Schema::SP getPrunedSchema() const;

    /**
     * Gets an approximation of how much memory the index uses.
     *
     * @return approximately how much memory is used by the index.
     **/
    MemoryUsage getMemoryUsage() const;

    uint64_t getStaticMemoryFootprint() const { return _staticMemoryFootprint; }
};

}
