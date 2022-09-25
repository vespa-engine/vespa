// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <atomic>
#include <mutex>

namespace search::index {
    class IFieldLengthInspector;
    class IndexBuilder;
}

namespace vespalib { class ISequencedTaskExecutor; }

namespace document { class Document; }

namespace search::memoryindex {

class DocumentInverterCollection;
class DocumentInverterContext;
class FieldIndexCollection;

/**
 * Memory index for a set of text and uri fields that uses lock-free B-Trees in underlying components.
 *
 * Each field is handled separately by a FieldIndex that contains postings lists for all unique words in that field.
 *
 * Documents are inserted and removed from the underlying field indexes in a two-step process:
 *   1) Call the async functions insertDocument() / removeDocument().
 *      This adds tasks to invert / remove the fields in the documents to the 'invert threads' executor.
 *   2) Call the async function commit().
 *      This adds tasks to push the changes into the field indexes to the 'push threads' executor.
 *      When commit is completed a completion callback is signaled.
 *
 * Use createBlueprint() to search the memory index for a given term in a given field.
 *
 */
class MemoryIndex : public queryeval::Searchable {
private:
    using ISequencedTaskExecutor = vespalib::ISequencedTaskExecutor;
    using LidVector = std::vector<uint32_t>;
    using OnWriteDoneType = const std::shared_ptr<vespalib::IDestructorCallback> &;
    index::Schema     _schema;
    ISequencedTaskExecutor &_invertThreads;
    ISequencedTaskExecutor &_pushThreads;
    std::unique_ptr<FieldIndexCollection> _fieldIndexes;
    std::unique_ptr<DocumentInverterContext> _inverter_context;
    std::unique_ptr<DocumentInverterCollection> _inverters;
    bool                _frozen;
    uint32_t            _maxDocId;
    std::atomic<uint32_t> _numDocs;
    mutable std::mutex  _lock;
    std::vector<bool>   _hiddenFields;
    index::Schema::SP   _prunedSchema;
    vespalib::hash_set<uint32_t> _indexedDocs; // documents in memory index
    const uint64_t      _staticMemoryFootprint;

    void updateMaxDocId(uint32_t docId) {
        if (docId > _maxDocId) {
            _maxDocId = docId;
        }
    }
    void incNumDocs() {
        auto num_docs = _numDocs.load(std::memory_order_relaxed);
        _numDocs.store(num_docs + 1, std::memory_order_relaxed);
    }
    void decNumDocs() {
        auto num_docs = _numDocs.load(std::memory_order_relaxed);
        if (num_docs > 0) {
            _numDocs.store(num_docs - 1, std::memory_order_relaxed);
        }
    }

public:
    using UP = std::unique_ptr<MemoryIndex>;
    using SP = std::shared_ptr<MemoryIndex>;

    /**
     * Create a new memory index based on the given schema.
     *
     * @param schema        the schema with which text and uri fields to keep in the index.
     * @param inspector     the inspector used to lookup initial field length info for all index fields.
     * @param invertThreads the executor with threads for doing document inverting.
     * @param pushThreads   the executor with threads for doing pushing of changes (inverted documents)
     *                      to corresponding field indexes.
     */
    MemoryIndex(const index::Schema& schema,
                const index::IFieldLengthInspector& inspector,
                ISequencedTaskExecutor& invertThreads,
                ISequencedTaskExecutor& pushThreads);

    MemoryIndex(const MemoryIndex &) = delete;
    MemoryIndex(MemoryIndex &&) = delete;
    MemoryIndex &operator=(const MemoryIndex &) = delete;
    MemoryIndex &operator=(MemoryIndex &&) = delete;
    ~MemoryIndex() override;

    const index::Schema &getSchema() const { return _schema; }

    bool isFrozen() const { return _frozen; }

    /**
     * Insert a document into the underlying field indexes.
     *
     * If the document is already in the index, the old version will be removed first.
     * This function is async. commit() must be called for changes to take effect.
     */
    void insertDocument(uint32_t docId, const document::Document &doc, OnWriteDoneType on_write_done);

    /**
     * Remove a document from the underlying field indexes.
     *
     * This function is async. commit() must be called for changes to take effect.
     */
    void removeDocuments(LidVector lids);

    /**
     * Commits the inserts and removes since the last commit, making them searchable.
     *
     * When commit is completed, 'on_write_done' goes out of scope, scheduling completion callback.
     */
    void commit(OnWriteDoneType on_write_done);

    /**
     * Freeze this index.
     *
     * Further index updates will be discarded.
     * Extra information kept to wash the posting lists will be discarded.
     */
    void freeze();

    /**
     * Dump the contents of this index into the given index builder.
     */
    void dump(index::IndexBuilder &indexBuilder);

    // Implements Searchable
    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpec &field,
                                                          const query::Node &term) override;

    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpecList &fields,
                                                          const query::Node &term) override
    {
        return queryeval::Searchable::createBlueprint(requestContext, fields, term);
    }

    virtual uint32_t getDocIdLimit() const {
        // Used to get docId range.
        return _maxDocId + 1;
    }

    virtual uint32_t getNumDocs() const {
        return _numDocs.load(std::memory_order_relaxed);
    }

    virtual uint64_t getNumWords() const;

    void pruneRemovedFields(const index::Schema &schema);

    index::Schema::SP getPrunedSchema() const;

    /**
     * Gets an approximation of how much memory the index uses.
     */
    vespalib::MemoryUsage getMemoryUsage() const;

    uint64_t getStaticMemoryFootprint() const { return _staticMemoryFootprint; }

    index::FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const;
};

}
