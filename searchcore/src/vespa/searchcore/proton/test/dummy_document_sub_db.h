// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/idocumentsubdb.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/server/document_subdb_initializer.h>
#include <vespa/searchcore/proton/server/isummaryadapter.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/server/ifeedview.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchcore/proton/server/reconfig_params.h>

namespace proton {

namespace test {

struct DummyDocumentSubDb : public IDocumentSubDB
{
    using IIndexManager = searchcorespi::IIndexManager;
    uint32_t                 _subDbId;
    DocumentMetaStoreContext _metaStoreCtx;
    ISummaryManager::SP      _summaryManager;
    IIndexManager::SP        _indexManager;
    ISummaryAdapter::SP      _summaryAdapter;
    IIndexWriter::SP         _indexWriter;
    std::unique_ptr<ExecutorThreadingService> _writeService;

    DummyDocumentSubDb(std::shared_ptr<BucketDBOwner> bucketDB, uint32_t subDbId)
        : _subDbId(subDbId),
          _metaStoreCtx(bucketDB),
          _summaryManager(),
          _indexManager(),
          _summaryAdapter(),
          _indexWriter(),
          _writeService(std::make_unique<ExecutorThreadingService>(1))
    {
    }
    ~DummyDocumentSubDb() {}
    void close() override { }
    uint32_t getSubDbId() const override { return _subDbId; }
    vespalib::string getName() const override { return "dummysubdb"; }
    DocumentSubDbInitializer::UP
    createInitializer(const DocumentDBConfig &,
                      SerialNum,
                      const vespa::config::search::core::ProtonConfig::
                      Summary &,
                      const vespa::config::search::core::
                      ProtonConfig::Index &) const override {
        return std::make_unique<DocumentSubDbInitializer>
            (const_cast<DummyDocumentSubDb &>(*this),
             _writeService->master());
    }
    void setup(const DocumentSubDbInitializerResult &) override {}
    void initViews(const DocumentDBConfig &,
                   const proton::matching::SessionManager::SP &) override {}
    IReprocessingTask::List applyConfig(const DocumentDBConfig &, const DocumentDBConfig &,
                                        SerialNum, const ReconfigParams &, IDocumentDBReferenceResolver &) override
    {
        return IReprocessingTask::List();
    }
    ISearchHandler::SP getSearchView() const override { return ISearchHandler::SP(); }
    IFeedView::SP getFeedView() const override { return IFeedView::SP(); }
    void clearViews() override {}
    const ISummaryManager::SP &getSummaryManager() const override { return _summaryManager; }
    proton::IAttributeManager::SP getAttributeManager() const override {
        return proton::IAttributeManager::SP();
    }
    const IIndexManager::SP &getIndexManager() const override { return _indexManager; }
    const ISummaryAdapter::SP &getSummaryAdapter() const override { return _summaryAdapter; }
    const IIndexWriter::SP &getIndexWriter() const override { return _indexWriter; }
    IDocumentMetaStoreContext &getDocumentMetaStoreContext() override { return _metaStoreCtx; }
    IFlushTargetList getFlushTargets() override { return IFlushTargetList(); }
    size_t getNumDocs() const override { return 0; }
    size_t getNumActiveDocs() const override { return 0; }
    bool hasDocument(const document::DocumentId &) override { return false; }
    void onReplayDone() override {}
    void onReprocessDone(SerialNum) override { }
    SerialNum getOldestFlushedSerial() override { return 0; }
    SerialNum getNewestFlushedSerial() override { return 0; }
    void pruneRemovedFields(SerialNum) override { }
    void setIndexSchema(const Schema::SP &, SerialNum) override { }
    search::SearchableStats getSearchableStats() const override {
        return search::SearchableStats();
    }
    IDocumentRetriever::UP getDocumentRetriever() override {
        return IDocumentRetriever::UP();
    }
    matching::MatchingStats getMatcherStats(const vespalib::string &) const override {
        return matching::MatchingStats();
    }
    virtual std::shared_ptr<IDocumentDBReference> getDocumentDBReference() override {
        return std::shared_ptr<IDocumentDBReference>();
    }
    virtual void tearDownReferences(IDocumentDBReferenceResolver &) override { }
};

} // namespace test

} // namespace proton

