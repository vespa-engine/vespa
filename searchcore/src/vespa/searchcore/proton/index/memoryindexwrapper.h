// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/memoryindex.h>
#include <vespa/searchcorespi/index/imemoryindex.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
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
    search::memoryindex::MemoryIndex _index;
    std::atomic<search::SerialNum> _serialNum;
    const search::common::FileHeaderContext &_fileHeaderContext;
    const search::TuneFileIndexing _tuneFileIndexing;

public:
    MemoryIndexWrapper(const search::index::Schema &schema,
                       const search::common::FileHeaderContext &fileHeaderContext,
                       const search::TuneFileIndexing &tuneFileIndexing,
                       searchcorespi::index::IThreadingService &
                       threadingService,
                       search::SerialNum serialNum);

    /**
     * Implements searchcorespi::IndexSearchable
     */
    virtual search::queryeval::Blueprint::UP
    createBlueprint(const search::queryeval::IRequestContext & requestContext,
                    const search::queryeval::FieldSpec &field,
                    const search::query::Node &term,
                    const search::attribute::IAttributeContext &) override
    {
        return _index.createBlueprint(requestContext, field, term);
    }
    virtual search::queryeval::Blueprint::UP
    createBlueprint(const search::queryeval::IRequestContext & requestContext,
                    const search::queryeval::FieldSpecList &fields,
                    const search::query::Node &term,
                    const search::attribute::IAttributeContext &) override
    {
        return _index.createBlueprint(requestContext, fields, term);
    }
    virtual search::SearchableStats getSearchableStats() const override {
        return search::SearchableStats()
            .memoryUsage(getMemoryUsage().allocatedBytes())
            .docsInMemory(_index.getNumDocs())
            .sizeOnDisk(0);
    }

    virtual search::SerialNum getSerialNum() const override;

    virtual void accept(searchcorespi::IndexSearchableVisitor &visitor) const override;

    /**
     * Implements proton::IMemoryIndex
     */
    virtual bool hasReceivedDocumentInsert() const override {
        return _index.getDocIdLimit() > 1u;
    }
    virtual search::index::Schema::SP getWipeTimeSchema() const override {
        return _index.getWipeTimeSchema();
    }
    virtual search::MemoryUsage getMemoryUsage() const override {
        return _index.getMemoryUsage();
    }
    virtual void insertDocument(uint32_t lid, const document::Document &doc) override {
        _index.insertDocument(lid, doc);
    }
    virtual void removeDocument(uint32_t lid) override {
        _index.removeDocument(lid);
    }
    uint64_t getStaticMemoryFootprint() const override {
        return _index.getStaticMemoryFootprint();
    }
    virtual void commit(OnWriteDoneType onWriteDone,
                        search::SerialNum serialNum) override {
        _index.commit(onWriteDone);
        _serialNum.store(serialNum, std::memory_order_relaxed);
    }
    virtual void wipeHistory(const search::index::Schema &schema)  override{
        _index.wipeHistory(schema);
    }
    virtual void flushToDisk(const vespalib::string &flushDir,
                             uint32_t docIdLimit,
                             search::SerialNum serialNum) override;
};

} // namespace proton
