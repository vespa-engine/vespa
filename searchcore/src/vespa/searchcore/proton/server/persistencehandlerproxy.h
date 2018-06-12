// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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


    void initialize() override;
    void handlePut(FeedToken token, const storage::spi::Bucket &bucket,
                   storage::spi::Timestamp timestamp, const DocumentSP &doc) override;

    void handleUpdate(FeedToken token, const storage::spi::Bucket &bucket,
                      storage::spi::Timestamp timestamp, const DocumentUpdateSP &upd) override;

    void handleRemove(FeedToken token, const storage::spi::Bucket &bucket,
                      storage::spi::Timestamp timestamp,
                      const document::DocumentId &id) override;

    void handleListBuckets(IBucketIdListResultHandler &resultHandler) override;
    void handleSetClusterState(const storage::spi::ClusterState &calc, IGenericResultHandler &resultHandler) override;

    void handleSetActiveState(const storage::spi::Bucket &bucket, storage::spi::BucketInfo::ActiveState newState,
                              IGenericResultHandler &resultHandler) override;

    void handleGetBucketInfo(const storage::spi::Bucket &bucket, IBucketInfoResultHandler &resultHandler) override;
    void handleCreateBucket(FeedToken token, const storage::spi::Bucket &bucket) override;
    void handleDeleteBucket(FeedToken token, const storage::spi::Bucket &bucket) override;
    void handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler) override;

    void handleSplit(FeedToken token, const storage::spi::Bucket &source,
                     const storage::spi::Bucket &target1, const storage::spi::Bucket &target2) override;

    void handleJoin(FeedToken token, const storage::spi::Bucket &source,
                    const storage::spi::Bucket &target1, const storage::spi::Bucket &target2) override;

    RetrieversSP getDocumentRetrievers(storage::spi::ReadConsistency consistency) override;
    BucketGuard::UP lockBucket(const storage::spi::Bucket &bucket) override;

    void handleListActiveBuckets(IBucketIdListResultHandler &resultHandler) override;

    void handlePopulateActiveBuckets(document::BucketId::List &buckets, IGenericResultHandler &resultHandler) override;
};

} // namespace proton

