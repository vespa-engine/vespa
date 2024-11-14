// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/memory_index.h>
#include <vespa/searchcorespi/index/imemoryindex.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <atomic>

namespace proton {

/**
 * Implementation of proton::IMemoryIndex by using search::memoryindex::MemoryIndex
 * as internal memory index.
 */
class MemoryIndexWrapper : public searchcorespi::index::IMemoryIndex {
private:
    using SerialNum = search::SerialNum;
    search::memoryindex::MemoryIndex _index;
    std::atomic<SerialNum> _serialNum;
    const search::common::FileHeaderContext &_fileHeaderContext;
    const search::TuneFileIndexing _tuneFileIndexing;

public:
    MemoryIndexWrapper(const search::index::Schema& schema,
                       const search::index::IFieldLengthInspector& inspector,
                       const search::common::FileHeaderContext& fileHeaderContext,
                       const search::TuneFileIndexing& tuneFileIndexing,
                       searchcorespi::index::IThreadingService& threadingService,
                       SerialNum serialNum);

    /**
     * Implements searchcorespi::IndexSearchable
     */
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const search::queryeval::IRequestContext & requestContext,
                    const search::queryeval::FieldSpec &field,
                    const search::query::Node &term) override
    {
        return _index.createBlueprint(requestContext, field, term);
    }
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const search::queryeval::IRequestContext & requestContext,
                    const search::queryeval::FieldSpecList &fields,
                    const search::query::Node &term) override
    {
        return _index.createBlueprint(requestContext, fields, term);
    }
    search::SearchableStats getSearchableStats() const override {
        return _index.get_stats();
    }

    SerialNum getSerialNum() const override;

    void accept(searchcorespi::IndexSearchableVisitor &visitor) const override;

    /**
     * Implements IFieldLengthInspector
     */
    search::index::FieldLengthInfo get_field_length_info(const std::string& field_name) const override;

    /**
     * Implements proton::IMemoryIndex
     */
    bool hasReceivedDocumentInsert() const override {
        return _index.getDocIdLimit() > 1u;
    }
    std::shared_ptr<const search::index::Schema> getPrunedSchema() const override {
        return _index.getPrunedSchema();
    }
    vespalib::MemoryUsage getMemoryUsage() const override {
        return _index.getMemoryUsage();
    }
    void insertDocument(uint32_t lid, const document::Document &doc, const OnWriteDoneType& on_write_done) override {
        _index.insertDocument(lid, doc, on_write_done);
    }
    void removeDocuments(LidVector lids) override {
        _index.removeDocuments(std::move(lids));
    }
    uint64_t getStaticMemoryFootprint() const override {
        return _index.getStaticMemoryFootprint();
    }
    void commit(const OnWriteDoneType& onWriteDone, SerialNum serialNum) override {
        _index.commit(onWriteDone);
        _serialNum.store(serialNum, std::memory_order_relaxed);
    }
    void pruneRemovedFields(const search::index::Schema &schema)  override {
        _index.pruneRemovedFields(schema);
    }
    void flushToDisk(const std::string &flushDir, uint32_t docIdLimit, SerialNum serialNum) override;

    void insert_write_context_state(vespalib::slime::Cursor& object) const override {
        _index.insert_write_context_state(object);
    }
};

} // namespace proton
