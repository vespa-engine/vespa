// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_task.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/util/searchable_stats.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <optional>

namespace search::index { class Schema; }

namespace document { class DocumentId; }

namespace searchcorespi {
    class IFlushTarget;
    class IIndexManager;
}
namespace proton::index {
    struct IndexConfig;
}

namespace proton {

namespace matching { class SessionManager; }

class DocumentDBConfig;
class DocumentSubDBReconfig;
class DocumentSubDbInitializer;
class DocumentSubDbInitializerResult;
class FeedHandler;
class IDocumentDBReference;
class IDocumentRetriever;
class IFeedView;
class IIndexWriter;
class IReplayConfig;
class ISearchHandler;
class ISummaryAdapter;
class ISummaryManager;
class PendingLidTrackerBase;
class ReconfigParams;
class RemoveDocumentsOperation;
class TransientResourceUsage;
struct IAttributeManager;
struct IBucketStateCalculator;
struct IDocumentDBReferenceResolver;
struct IDocumentMetaStoreContext;

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
    using UP = std::unique_ptr<IDocumentSubDB>;
    using SerialNum = search::SerialNum;
    using Schema = search::index::Schema;
    using SchemaSP = std::shared_ptr<Schema>;
    using IFlushTargetList = std::vector<std::shared_ptr<searchcorespi::IFlushTarget>>;
    using IndexConfig = index::IndexConfig;
    using OnDone = std::shared_ptr<vespalib::IDestructorCallback>;
    using SessionManager = matching::SessionManager;
public:
    IDocumentSubDB() { }
    virtual ~IDocumentSubDB() { }
    virtual uint32_t getSubDbId() const = 0;
    virtual vespalib::string getName() const = 0;

    virtual std::unique_ptr<DocumentSubDbInitializer>
    createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                      const IndexConfig &indexCfg) const = 0;

    // Called by master thread
    virtual void setup(const DocumentSubDbInitializerResult &initResult) = 0;
    virtual void initViews(const DocumentDBConfig &configSnapshot) = 0;

    virtual std::unique_ptr<DocumentSubDBReconfig>
    prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const ReconfigParams& reconfig_params, std::optional<SerialNum> serial_num) = 0;
    virtual void complete_prepare_reconfig(DocumentSubDBReconfig& prepared_reconfig, SerialNum serial_num) = 0;
    virtual IReprocessingTask::List
    applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver, const DocumentSubDBReconfig& prepared_reconfig) = 0;
    virtual void setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> &calc, OnDone) = 0;

    virtual std::shared_ptr<ISearchHandler> getSearchView() const = 0;
    virtual std::shared_ptr<IFeedView> getFeedView() const = 0;
    virtual void clearViews() = 0;
    virtual const std::shared_ptr<ISummaryManager> &getSummaryManager() const = 0;
    virtual std::shared_ptr<IAttributeManager> getAttributeManager() const = 0;
    virtual const std::shared_ptr<searchcorespi::IIndexManager> &getIndexManager() const = 0;
    virtual const std::shared_ptr<ISummaryAdapter> &getSummaryAdapter() const = 0;
    virtual const std::shared_ptr<IIndexWriter> &getIndexWriter() const = 0;
    virtual IDocumentMetaStoreContext &getDocumentMetaStoreContext() = 0;
    virtual const IDocumentMetaStoreContext &getDocumentMetaStoreContext() const = 0;
    virtual IFlushTargetList getFlushTargets() = 0;
    virtual size_t getNumDocs() const = 0;
    virtual size_t getNumActiveDocs() const = 0;
    /**
     * Needed by FeedRouter::handleRemove().
     * TODO: remove together with FeedEngine.
     **/
    virtual bool hasDocument(const document::DocumentId &id) = 0;
    virtual void onReplayDone() = 0;
    virtual void onReprocessDone(SerialNum serialNum) = 0;

    /*
     * Get oldest flushed serial for components.
     */
    virtual SerialNum getOldestFlushedSerial() = 0;

    /*
     * Get newest flushed serial.  Used to validate that we've not lost
     * last part of transaction log.
     */
    virtual SerialNum getNewestFlushedSerial()  = 0;
    virtual void pruneRemovedFields(SerialNum serialNum) = 0;
    virtual void setIndexSchema(const SchemaSP &schema, SerialNum serialNum) = 0;
    virtual search::SearchableStats getSearchableStats() const = 0;
    virtual std::unique_ptr<IDocumentRetriever> getDocumentRetriever() = 0;

    virtual matching::MatchingStats getMatcherStats(const vespalib::string &rankProfile) const = 0;
    virtual void close() = 0;
    virtual std::shared_ptr<IDocumentDBReference> getDocumentDBReference() = 0;
    virtual void tearDownReferences(IDocumentDBReferenceResolver &resolver) = 0;
    virtual void validateDocStore(FeedHandler &op, SerialNum serialNum) const = 0;
    virtual PendingLidTrackerBase & getUncommittedLidsTracker() = 0;
    virtual TransientResourceUsage get_transient_resource_usage() const = 0;
};

} // namespace proton
