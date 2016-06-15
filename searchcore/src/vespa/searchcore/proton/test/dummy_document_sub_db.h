// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/idocumentsubdb.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>

namespace proton {

namespace test {

struct DummyDocumentSubDb : public IDocumentSubDB
{
    uint32_t                 _subDbId;
    DocumentMetaStoreContext _metaStoreCtx;
    ISummaryManager::SP      _summaryManager;
    IIndexManager::SP        _indexManager;
    ISummaryAdapter::SP      _summaryAdapter;
    IIndexWriter::SP         _indexWriter;
    std::unique_ptr<ExecutorThreadingService> _writeService;

    DummyDocumentSubDb(std::shared_ptr<BucketDBOwner> bucketDB,
                       uint32_t subDbId)
        : _subDbId(subDbId),
          _metaStoreCtx(bucketDB),
          _summaryManager(),
          _indexManager(),
          _summaryAdapter(),
          _indexWriter(),
          _writeService(std::make_unique<ExecutorThreadingService>(1))
    {
    }
    void close() override { }
    virtual uint32_t getSubDbId() const override { return _subDbId; }
    virtual vespalib::string getName() const override { return "dummysubdb"; }
    virtual DocumentSubDbInitializer::UP
    createInitializer(const DocumentDBConfig &,
                      SerialNum,
                      const search::index::Schema::SP &,
                      const vespa::config::search::core::ProtonConfig::
                      Summary &,
                      const vespa::config::search::core::
                      ProtonConfig::Index &) const override {
        return std::make_unique<DocumentSubDbInitializer>
            (const_cast<DummyDocumentSubDb &>(*this),
             _writeService->master());
    }
    virtual void setup(const DocumentSubDbInitializerResult &) override {}
    virtual void initViews(const DocumentDBConfig &,
                           const proton::matching::SessionManager::SP &) override {}
    virtual IReprocessingTask::List applyConfig(const DocumentDBConfig &,
                                                const DocumentDBConfig &,
                                                SerialNum,
                                                const ReconfigParams) override {
        return IReprocessingTask::List();
    }
    virtual ISearchHandler::SP getSearchView() const override { return ISearchHandler::SP(); }
    virtual IFeedView::SP getFeedView() const override { return IFeedView::SP(); }
    virtual void clearViews() override {}
    virtual const ISummaryManager::SP &getSummaryManager() const override { return _summaryManager; }
    virtual proton::IAttributeManager::SP getAttributeManager() const override {
        return proton::IAttributeManager::SP();
    }
    virtual const IIndexManager::SP &getIndexManager() const override { return _indexManager; }
    virtual const ISummaryAdapter::SP &getSummaryAdapter() const override { return _summaryAdapter; }
    virtual const IIndexWriter::SP &getIndexWriter() const override { return _indexWriter; }
    virtual IDocumentMetaStoreContext &getDocumentMetaStoreContext() override { return _metaStoreCtx; }
    virtual IFlushTarget::List getFlushTargets() override { return IFlushTarget::List(); }
    virtual size_t getNumDocs() const override { return 0; }
    virtual size_t getNumActiveDocs() const override { return 0; }
    virtual bool hasDocument(const document::DocumentId &) override { return false; }
    virtual void onReplayDone() override {}
    virtual void onReprocessDone(SerialNum) override { }
    virtual SerialNum getOldestFlushedSerial() override { return 0; }
    virtual SerialNum getNewestFlushedSerial() override { return 0; }
    virtual void wipeHistory(SerialNum,
                             const search::index::Schema &,
                             const search::index::Schema &) override {}
    virtual void setIndexSchema(const search::index::Schema::SP &,
                                const search::index::Schema::SP &) override {}
    virtual search::SearchableStats getSearchableStats(void) const override {
        return search::SearchableStats();
    }
    virtual IDocumentRetriever::UP getDocumentRetriever() override {
        return IDocumentRetriever::UP();
    }
    virtual matching::MatchingStats getMatcherStats(const vespalib::string &) const override {
        return matching::MatchingStats();
    }

};

} // namespace test

} // namespace proton

