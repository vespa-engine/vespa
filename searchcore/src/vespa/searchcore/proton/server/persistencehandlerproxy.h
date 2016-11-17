// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/persistenceengine/ipersistencehandler.h>

namespace proton {

class DocumentDB;
class FeedHandler;
class BucketHandler;
class ClusterStateHandler;
class PersistenceHandlerProxy : public IPersistenceHandler
{
private:
    std::shared_ptr<DocumentDB>       _documentDB;
    FeedHandler         &_feedHandler;
    BucketHandler       &_bucketHandler;
    ClusterStateHandler &_clusterStateHandler;
public:
    PersistenceHandlerProxy(const std::shared_ptr<DocumentDB> &documentDB);

    virtual ~PersistenceHandlerProxy();

    /**
     * Implements IPersistenceHandler.
     */
    virtual void initialize() override;

    virtual void handlePut(FeedToken token,
                           const storage::spi::Bucket &bucket,
                           storage::spi::Timestamp timestamp,
                           const document::Document::SP &doc) override;

    virtual void handleUpdate(FeedToken token,
                              const storage::spi::Bucket &bucket,
                              storage::spi::Timestamp timestamp,
                              const document::DocumentUpdate::SP &upd) override;

    virtual void handleRemove(FeedToken token,
                              const storage::spi::Bucket &bucket,
                              storage::spi::Timestamp timestamp,
                              const document::DocumentId &id) override;

    virtual void handleListBuckets(IBucketIdListResultHandler &resultHandler) override;

    virtual void handleSetClusterState(const storage::spi::ClusterState &calc,
                                       IGenericResultHandler &resultHandler) override;

    virtual void handleSetActiveState(const storage::spi::Bucket &bucket,
                                      storage::spi::BucketInfo::ActiveState newState,
                                      IGenericResultHandler &resultHandler) override;

    virtual void handleGetBucketInfo(const storage::spi::Bucket &bucket,
                                     IBucketInfoResultHandler &resultHandler) override;

    virtual void
    handleCreateBucket(FeedToken token,
                       const storage::spi::Bucket &bucket) override;

    virtual void handleDeleteBucket(FeedToken token,
                                    const storage::spi::Bucket &bucket) override;

    virtual void handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler) override;

    virtual void
    handleSplit(FeedToken token,
                const storage::spi::Bucket &source,
                const storage::spi::Bucket &target1,
                const storage::spi::Bucket &target2) override;

    virtual void
    handleJoin(FeedToken token,
               const storage::spi::Bucket &source,
               const storage::spi::Bucket &target1,
               const storage::spi::Bucket &target2) override;

    virtual RetrieversSP getDocumentRetrievers(storage::spi::ReadConsistency consistency) override;
    virtual BucketGuard::UP lockBucket(const storage::spi::Bucket &bucket) override;

    virtual void
    handleListActiveBuckets(IBucketIdListResultHandler &resultHandler) override;

    virtual void
    handlePopulateActiveBuckets(document::BucketId::List &buckets,
                                IGenericResultHandler &resultHandler) override;
};

} // namespace proton

