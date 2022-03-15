// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencehandlerproxy.h"
#include "documentdb.h"
#include "feedhandler.h"
#include <vespa/searchcore/proton/feedoperation/createbucketoperation.h>
#include <vespa/searchcore/proton/feedoperation/deletebucketoperation.h>
#include <vespa/searchcore/proton/feedoperation/joinbucketsoperation.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchcore/proton/feedoperation/splitbucketoperation.h>
#include <vespa/searchcore/proton/feedoperation/updateoperation.h>
#include <vespa/document/update/documentupdate.h>

using storage::spi::Bucket;
using storage::spi::Timestamp;

namespace proton {

PersistenceHandlerProxy::PersistenceHandlerProxy(DocumentDB::SP documentDB)
    : _documentDB(std::move(documentDB)),
      _feedHandler(_documentDB->getFeedHandler()),
      _bucketHandler(_documentDB->getBucketHandler()),
      _clusterStateHandler(_documentDB->getClusterStateHandler()),
      _retainGuard(_documentDB->retain())
{ }

PersistenceHandlerProxy::~PersistenceHandlerProxy() = default;

void
PersistenceHandlerProxy::initialize()
{
    _documentDB->waitForOnlineState();
}

void
PersistenceHandlerProxy::handlePut(FeedToken token, const Bucket &bucket, Timestamp timestamp, DocumentSP doc)
{
    auto op = std::make_unique<PutOperation>(bucket.getBucketId().stripUnused(), timestamp, std::move(doc));
    _feedHandler.handleOperation(std::move(token), std::move(op));
}

void
PersistenceHandlerProxy::handleUpdate(FeedToken token, const Bucket &bucket, Timestamp timestamp, DocumentUpdateSP upd)
{
    auto op = std::make_unique<UpdateOperation>(bucket.getBucketId().stripUnused(), timestamp, std::move(upd));
    _feedHandler.handleOperation(std::move(token), std::move(op));
}

void
PersistenceHandlerProxy::handleRemove(FeedToken token, const Bucket &bucket, Timestamp timestamp, const document::DocumentId &id)
{
    auto op = std::make_unique<RemoveOperationWithDocId>(bucket.getBucketId().stripUnused(), timestamp, id);
    _feedHandler.handleOperation(std::move(token), std::move(op));
}

void
PersistenceHandlerProxy::handleListBuckets(IBucketIdListResultHandler &resultHandler)
{
    _bucketHandler.handleListBuckets(resultHandler);
}

void
PersistenceHandlerProxy::handleSetClusterState(const storage::spi::ClusterState &calc, IGenericResultHandler &resultHandler)
{
    _clusterStateHandler.handleSetClusterState(calc, resultHandler);
}

void
PersistenceHandlerProxy::handleSetActiveState(const storage::spi::Bucket &bucket,
                                              storage::spi::BucketInfo::ActiveState newState,
                                              std::shared_ptr<IGenericResultHandler> resultHandler)
{
    _bucketHandler.handleSetCurrentState(bucket.getBucketId().stripUnused(), newState, std::move(resultHandler));
}

void
PersistenceHandlerProxy::handleGetBucketInfo(const Bucket &bucket, IBucketInfoResultHandler &resultHandler)
{
    _bucketHandler.handleGetBucketInfo(bucket, resultHandler);
}

void
PersistenceHandlerProxy::handleCreateBucket(FeedToken token, const Bucket &bucket)
{
    if ( ! _bucketHandler.hasBucket(bucket)) {
        auto op = std::make_unique<CreateBucketOperation>(bucket.getBucketId().stripUnused());
        _feedHandler.handleOperation(std::move(token), std::move(op));
    }
}

void
PersistenceHandlerProxy::handleDeleteBucket(FeedToken token, const Bucket &bucket)
{
    auto op = std::make_unique<DeleteBucketOperation>(bucket.getBucketId().stripUnused());
    _feedHandler.handleOperation(std::move(token), std::move(op));
}

void
PersistenceHandlerProxy::handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler)
{
    _clusterStateHandler.handleGetModifiedBuckets(resultHandler);
}

void
PersistenceHandlerProxy::handleSplit(FeedToken token, const Bucket &source, const Bucket &target1, const Bucket &target2)
{
    auto op = std::make_unique<SplitBucketOperation>(source.getBucketId().stripUnused(),
                                                     target1.getBucketId().stripUnused(),
                                                     target2.getBucketId().stripUnused());
    _feedHandler.handleOperation(std::move(token), std::move(op));
}

void
PersistenceHandlerProxy::handleJoin(FeedToken token, const Bucket &source1, const Bucket &source2, const Bucket &target)
{
    auto op = std::make_unique<JoinBucketsOperation>(source1.getBucketId().stripUnused(),
                                                     source2.getBucketId().stripUnused(),
                                                     target.getBucketId().stripUnused());
    _feedHandler.handleOperation(std::move(token), std::move(op));
}

IPersistenceHandler::RetrieversSP
PersistenceHandlerProxy::getDocumentRetrievers(storage::spi::ReadConsistency consistency)
{
    return _documentDB->getDocumentRetrievers(consistency);
}

void
PersistenceHandlerProxy::handleListActiveBuckets(IBucketIdListResultHandler &resultHandler)
{
    _bucketHandler.handleListActiveBuckets(resultHandler);
}

void
PersistenceHandlerProxy::handlePopulateActiveBuckets(document::BucketId::List buckets, IGenericResultHandler &resultHandler)
{
    _bucketHandler.handlePopulateActiveBuckets(std::move(buckets), resultHandler);
}

} // namespace proton
