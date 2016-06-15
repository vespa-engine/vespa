// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_subdb_initializer.h"
#include "ifeedview.h"
#include "searchable_doc_subdb_configurer.h"
#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/matchengine/imatchhandler.h>
#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_task.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/util/searchable_stats.h>

namespace document
{

class DocumentId;

}

namespace proton
{

class FeedHandler;
class DocumentDBConfig;
class FileConfigManager;
class IReplayConfig;

/**
 * Interface for a document sub database that handles a subset of the documents that belong to a
 * DocumentDB.
 *
 * Documents can be inserted/updated/removed to a sub database via a feed view,
 * searched via a search view and retrieved via a document retriever.
 * A sub database is separate and independent from other sub databases.
 */
class IDocumentSubDB
{
public:
    class IOwner
    {
    public:
        virtual ~IOwner() {}
        virtual void syncFeedView() = 0;
        virtual searchcorespi::IIndexManagerFactory::SP
        getIndexManagerFactory(const vespalib::stringref &name) const = 0;
        virtual vespalib::string getName() const = 0;
        virtual uint32_t getDistributionKey() const = 0;
    };

    typedef std::unique_ptr<IDocumentSubDB>              UP;
    typedef search::SerialNum      SerialNum;

public:
    IDocumentSubDB() { }

    virtual ~IDocumentSubDB() { }

    virtual uint32_t getSubDbId() const = 0;

    virtual vespalib::string getName() const = 0;

    virtual DocumentSubDbInitializer::UP
    createInitializer(const DocumentDBConfig &configSnapshot,
                      SerialNum configSerialNum,
                      const search::index::Schema::SP &unionSchema,
                      const vespa::config::search::core::
                      ProtonConfig::Summary &protonSummaryCfg,
                      const vespa::config::search::core::
                      ProtonConfig::Index &indexCfg) const = 0;

    // Called by master thread
    virtual void setup(const DocumentSubDbInitializerResult &initResult) = 0;

    virtual void
    initViews(const DocumentDBConfig &configSnapshot,
              const proton::matching::SessionManager::SP &sessionManager) = 0;

    virtual IReprocessingTask::List
    applyConfig(const DocumentDBConfig &newConfigSnapshot,
                const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum,
                const ReconfigParams params) = 0;

    virtual ISearchHandler::SP
    getSearchView(void) const = 0;

    virtual IFeedView::SP
    getFeedView(void) const = 0;

    virtual void
    clearViews(void) = 0;

    virtual const ISummaryManager::SP &
    getSummaryManager() const = 0;

    virtual proton::IAttributeManager::SP
    getAttributeManager(void) const = 0;

    virtual const IIndexManager::SP &
    getIndexManager(void) const = 0;

    virtual const ISummaryAdapter::SP &
    getSummaryAdapter(void) const = 0;

    virtual const IIndexWriter::SP &
    getIndexWriter(void) const = 0;

    virtual IDocumentMetaStoreContext &
    getDocumentMetaStoreContext() = 0;

    virtual IFlushTarget::List
    getFlushTargets(void) = 0;

    virtual size_t
    getNumDocs(void) const = 0;

    virtual size_t
    getNumActiveDocs(void) const = 0;

    /**
     * Needed by FeedRouter::handleRemove().
     * TODO: remove together with FeedEngine.
     **/
    virtual bool
    hasDocument(const document::DocumentId &id) = 0;

    virtual void
    onReplayDone(void) = 0;

    virtual void
    onReprocessDone(SerialNum serialNum) = 0;

    /*
     * Get oldest flushed serial for components.
     */
    virtual SerialNum
    getOldestFlushedSerial(void) = 0;

    /*
     * Get newest flushed serial.  Used to validate that we've not lost
     * last part of transaction log.
     */
    virtual SerialNum
    getNewestFlushedSerial()  = 0;

    virtual void
    wipeHistory(SerialNum wipeSerial,
                const search::index::Schema &newHistorySchema,
                const search::index::Schema &wipeSchema) = 0;

    virtual void
    setIndexSchema(const search::index::Schema::SP &schema,
                   const search::index::Schema::SP &fusionSchema) = 0;

    virtual search::SearchableStats
    getSearchableStats(void) const = 0;

    virtual IDocumentRetriever::UP
    getDocumentRetriever(void) = 0;

    virtual matching::MatchingStats
    getMatcherStats(const vespalib::string &rankProfile) const = 0;

    virtual void close() = 0;
};

} // namespace proton

