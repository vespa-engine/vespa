// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_retriever.h"
#include "resulthandler.h"
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace document {
    class Document;
    class DocumentUpdate;
}
namespace storage::spi { class ClusterState; }

namespace proton {

class DocTypeName;

/**
 * This interface describes a sync persistence operation handler. It is implemented by
 * the DocumentDB and other classes, and used by the PersistenceEngine class to delegate
 * operations to the appropriate db.
 */
class IPersistenceHandler {
protected:
    IPersistenceHandler() = default;
    using DocumentUpdateSP = std::shared_ptr<document::DocumentUpdate>;
    using DocumentSP = std::shared_ptr<document::Document>;
public:
    using UP = std::unique_ptr<IPersistenceHandler>;
    using SP = std::shared_ptr<IPersistenceHandler>;
    // Note that you can not move away the handlers in the vector.
    using RetrieversSP = std::shared_ptr<std::vector<IDocumentRetriever::SP> >;
    IPersistenceHandler(const IPersistenceHandler &) = delete;
    IPersistenceHandler & operator = (const IPersistenceHandler &) = delete;

    virtual ~IPersistenceHandler() = default;

    /**
     * Called before all other functions so that the persistence handler
     * can initialize itself before being used.
     */
    virtual void initialize() = 0;

    virtual void handlePut(FeedToken token, const storage::spi::Bucket &bucket,
                           storage::spi::Timestamp timestamp, DocumentSP doc) = 0;

    virtual void handleUpdate(FeedToken token, const storage::spi::Bucket &bucket,
                              storage::spi::Timestamp timestamp, DocumentUpdateSP upd) = 0;

    virtual void handleRemove(FeedToken token, const storage::spi::Bucket &bucket,
                              storage::spi::Timestamp timestamp, const document::DocumentId &id) = 0;
    virtual void handleRemoveByGid(FeedToken token, const storage::spi::Bucket &bucket,
                                   storage::spi::Timestamp timestamp,
                                   vespalib::stringref doc_type, const document::GlobalId& gid) = 0;

    virtual void handleListBuckets(IBucketIdListResultHandler &resultHandler) = 0;
    virtual void handleSetClusterState(const storage::spi::ClusterState &calc, IGenericResultHandler &resultHandler) = 0;

    virtual void handleSetActiveState(const storage::spi::Bucket &bucket,
                                      storage::spi::BucketInfo::ActiveState newState,
                                      std::shared_ptr<IGenericResultHandler> resultHandler) = 0;

    virtual void handleGetBucketInfo(const storage::spi::Bucket &bucket, IBucketInfoResultHandler &resultHandler) = 0;
    virtual void handleCreateBucket(FeedToken token, const storage::spi::Bucket &bucket) = 0;
    virtual void handleDeleteBucket(FeedToken token, const storage::spi::Bucket &bucket) = 0;
    virtual void handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler) = 0;

    virtual void handleSplit(FeedToken token, const storage::spi::Bucket &source,
                             const storage::spi::Bucket &target1, const storage::spi::Bucket &target2) = 0;

    virtual void handleJoin(FeedToken token, const storage::spi::Bucket &source,
                            const storage::spi::Bucket &target1, const storage::spi::Bucket &target2) = 0;

    virtual RetrieversSP getDocumentRetrievers(storage::spi::ReadConsistency consistency) = 0;

    virtual void handleListActiveBuckets(IBucketIdListResultHandler &resultHandler) = 0;

    virtual void handlePopulateActiveBuckets(document::BucketId::List buckets, IGenericResultHandler &resultHandler) = 0;

    [[nodiscard]] virtual const DocTypeName &doc_type_name() const noexcept = 0;
};

} // namespace proton

