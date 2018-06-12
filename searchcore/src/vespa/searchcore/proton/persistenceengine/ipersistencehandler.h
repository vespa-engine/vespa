// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucket_guard.h"
#include "i_document_retriever.h"
#include "resulthandler.h"
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace document {
    class Document;
    class DocumentUpdate;
}
namespace proton {

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
    using RetrieversSP = std::shared_ptr<std::vector<IDocumentRetriever::SP> >;
    IPersistenceHandler(const IPersistenceHandler &) = delete;
    IPersistenceHandler & operator = (const IPersistenceHandler &) = delete;

    /**
     * Virtual destructor to allow inheritance.
     */
    virtual ~IPersistenceHandler() { }

    /**
     * Called before all other functions so that the persistence handler
     * can initialize itself before being used.
     */
    virtual void initialize() = 0;

    virtual void handlePut(FeedToken token, const storage::spi::Bucket &bucket,
                           storage::spi::Timestamp timestamp, const DocumentSP &doc) = 0;

    virtual void handleUpdate(FeedToken token, const storage::spi::Bucket &bucket,
                              storage::spi::Timestamp timestamp, const DocumentUpdateSP &upd) = 0;

    virtual void handleRemove(FeedToken token, const storage::spi::Bucket &bucket,
                              storage::spi::Timestamp timestamp, const document::DocumentId &id) = 0;

    virtual void handleListBuckets(IBucketIdListResultHandler &resultHandler) = 0;
    virtual void handleSetClusterState(const storage::spi::ClusterState &calc, IGenericResultHandler &resultHandler) = 0;

    virtual void handleSetActiveState(const storage::spi::Bucket &bucket,
                                      storage::spi::BucketInfo::ActiveState newState,
                                      IGenericResultHandler &resultHandler) = 0;

    virtual void handleGetBucketInfo(const storage::spi::Bucket &bucket, IBucketInfoResultHandler &resultHandler) = 0;
    virtual void handleCreateBucket(FeedToken token, const storage::spi::Bucket &bucket) = 0;
    virtual void handleDeleteBucket(FeedToken token, const storage::spi::Bucket &bucket) = 0;
    virtual void handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler) = 0;

    virtual void handleSplit(FeedToken token, const storage::spi::Bucket &source,
                             const storage::spi::Bucket &target1, const storage::spi::Bucket &target2) = 0;

    virtual void handleJoin(FeedToken token, const storage::spi::Bucket &source,
                            const storage::spi::Bucket &target1, const storage::spi::Bucket &target2) = 0;

    virtual RetrieversSP getDocumentRetrievers(storage::spi::ReadConsistency consistency) = 0;
    virtual BucketGuard::UP lockBucket(const storage::spi::Bucket &bucket) = 0;

    virtual void handleListActiveBuckets(IBucketIdListResultHandler &resultHandler) = 0;

    virtual void handlePopulateActiveBuckets(document::BucketId::List &buckets, IGenericResultHandler &resultHandler) = 0;
};

} // namespace proton

