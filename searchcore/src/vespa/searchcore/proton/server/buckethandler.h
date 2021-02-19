// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iclusterstatechangedhandler.h"
#include "ibucketstatechangednotifier.h"
#include <vespa/searchcore/proton/documentmetastore/i_bucket_handler.h>
#include <vespa/searchcore/proton/persistenceengine/resulthandler.h>
#include <vespa/vespalib/util/executor.h>

namespace proton {

class IBucketStateChangedhandler;


/**
 * Class handling bucket operations in IPersistenceHandler that are not persisted
 * in the transaction log for a document database.
 */
class BucketHandler : public IClusterStateChangedHandler,
                      public IBucketStateChangedNotifier
{
private:
    vespalib::Executor                       &_executor;
    documentmetastore::IBucketHandler        *_ready;
    std::vector<IBucketStateChangedHandler *> _changedHandlers;
    bool                                      _nodeUp;

    void performSetCurrentState(document::BucketId bucketId,
                                storage::spi::BucketInfo::ActiveState newState,
                                IGenericResultHandler *resultHandler);

    void performPopulateActiveBuckets(document::BucketId::List buckets,
                                      IGenericResultHandler *resultHandler);
    /**
     * Deactivate all active buckets when this node transitions from
     * up to down in cluster state.  Called by document db executor thread.
     */
    void deactivateAllActiveBuckets();

public:
    /**
     * Create a new bucket handler.
     *
     * @param executor The executor in which to run all tasks.
     */
    BucketHandler(vespalib::Executor &executor);
    ~BucketHandler() override;

    void setReadyBucketHandler(documentmetastore::IBucketHandler &ready);

    /**
     * Implements the bucket aspect of IPersistenceHandler.
     */
    void handleListBuckets(IBucketIdListResultHandler &resultHandler);
    void handleSetCurrentState(const document::BucketId &bucketId,
                               storage::spi::BucketInfo::ActiveState newState,
                               IGenericResultHandler &resultHandler);
    void handleGetBucketInfo(const storage::spi::Bucket &bucket,
                             IBucketInfoResultHandler &resultHandler);
    void handleListActiveBuckets(IBucketIdListResultHandler &resultHandler);
    void handlePopulateActiveBuckets(document::BucketId::List &buckets,
                                     IGenericResultHandler &resultHandler);

    // Implements IClusterStateChangedHandler
    void notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc) override;

    // Implement IBucketStateChangedNotifier
    void addBucketStateChangedHandler(IBucketStateChangedHandler *handler) override;
    void removeBucketStateChangedHandler(IBucketStateChangedHandler *handler) override;
};

} // namespace proton

