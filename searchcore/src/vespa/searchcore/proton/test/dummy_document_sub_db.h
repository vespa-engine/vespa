// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "transport_helper.h"
#include <vespa/searchcore/proton/server/idocumentsubdb.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/server/document_subdb_initializer.h>
#include <vespa/searchcore/proton/server/document_subdb_reconfig.h>
#include <vespa/searchcore/proton/server/isummaryadapter.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/server/ifeedview.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchcore/proton/server/reconfig_params.h>
#include <vespa/searchcore/proton/common/pendinglidtracker.h>

namespace proton::test {

struct DummyDocumentSubDb : public IDocumentSubDB
{
    using IIndexManager = searchcorespi::IIndexManager;
    uint32_t                    _subDbId;
    DocumentMetaStoreContext    _metaStoreCtx;
    ISummaryManager::SP         _summaryManager;
    IIndexManager::SP           _indexManager;
    ISummaryAdapter::SP         _summaryAdapter;
    IIndexWriter::SP            _indexWriter;
    mutable TransportAndExecutorService _service;
    PendingLidTracker           _pendingLidTracker;

    DummyDocumentSubDb(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB, uint32_t subDbId);
    ~DummyDocumentSubDb() override;
    void close() override { }
    uint32_t getSubDbId() const override { return _subDbId; }
    std::string getName() const override { return "dummysubdb"; }
    DocumentSubDbInitializer::UP
    createInitializer(const DocumentDBConfig &, SerialNum,const index::IndexConfig &) const override {
        return std::make_unique<DocumentSubDbInitializer>
            (const_cast<DummyDocumentSubDb &>(*this), _service.write().master());
    }
    void setup(const DocumentSubDbInitializerResult &) override {}
    void initViews(const DocumentDBConfig &) override {}
    std::unique_ptr<DocumentSubDBReconfig> prepare_reconfig(const DocumentDBConfig&, const ReconfigParams&, std::optional<SerialNum>) override {
        return std::make_unique<DocumentSubDBReconfig>(std::shared_ptr<Matchers>(), std::shared_ptr<IAttributeManager>());
    }
    void complete_prepare_reconfig(DocumentSubDBReconfig&, SerialNum) override { }
    IReprocessingTask::List applyConfig(const DocumentDBConfig &, const DocumentDBConfig &,
                                        SerialNum, const ReconfigParams &, IDocumentDBReferenceResolver &, const DocumentSubDBReconfig&) override
    {
        return {};
    }
    void setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> &, OnDone) override { }
    ISearchHandler::SP getSearchView() const override { return {}; }
    IFeedView::SP getFeedView() const override { return {}; }
    void clearViews() override {}
    const ISummaryManager::SP &getSummaryManager() const override { return _summaryManager; }
    std::shared_ptr<IAttributeWriter> get_attribute_writer() const override { return {}; }
    proton::IAttributeManager::SP getAttributeManager() const override { return {}; }
    void validateDocStore(FeedHandler &, SerialNum ) const override {}
    const IIndexManager::SP &getIndexManager() const override { return _indexManager; }
    const ISummaryAdapter::SP &getSummaryAdapter() const override { return _summaryAdapter; }
    const IIndexWriter::SP &getIndexWriter() const override { return _indexWriter; }
    IDocumentMetaStoreContext &getDocumentMetaStoreContext() override { return _metaStoreCtx; }
    const IDocumentMetaStoreContext &getDocumentMetaStoreContext() const override { return _metaStoreCtx; }
    IFlushTargetList getFlushTargets() override { return {}; }
    size_t getNumDocs() const override { return 0; }
    size_t getNumActiveDocs() const override { return 0; }
    bool hasDocument(const document::DocumentId &) override { return false; }
    void onReplayDone() override {}
    void onReprocessDone(SerialNum) override { }
    SerialNum getOldestFlushedSerial() override { return 0; }
    SerialNum getNewestFlushedSerial() override { return 0; }
    void pruneRemovedFields(SerialNum) override { }
    void setIndexSchema(std::shared_ptr<const Schema>, SerialNum) override { }
    search::SearchableStats getSearchableStats(bool) const override {
        return {};
    }
    std::shared_ptr<IDocumentRetriever> getDocumentRetriever() override {
        return {};
    }
    matching::MatchingStats getMatcherStats(const std::string &) const override {
        return {};
    }
    std::shared_ptr<IDocumentDBReference> getDocumentDBReference() override {
        return {};
    }

    PendingLidTrackerBase &getUncommittedLidsTracker() override {
        return _pendingLidTracker;
    }

    void tearDownReferences(IDocumentDBReferenceResolver &) override { }
    TransientResourceUsage get_transient_resource_usage() const override { return {}; }
};

}
