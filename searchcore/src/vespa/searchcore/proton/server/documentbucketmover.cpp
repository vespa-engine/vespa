// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentbucketmover.h"
#include "i_move_operation_limiter.h"
#include "idocumentmovehandler.h"
#include "maintenancedocumentsubdb.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/destructor_callbacks.h>

using document::BucketId;
using document::Document;
using document::GlobalId;
using storage::spi::Timestamp;

namespace proton::bucketdb {

typedef IDocumentMetaStore::Iterator Iterator;

MoveOperation::UP
BucketMover::createMoveOperation(const MoveKey &key) {
    if (_source->lidNeedsCommit(key._lid)) {
        return {};
    }
    Document::SP doc(_source->retriever()->getFullDocument(key._lid));
    if (!doc || doc->getId().getGlobalId() != key._gid)
        return {}; // Failed to retrieve document, removed or changed identity
    BucketId bucketId = _bucket.stripUnused();
    return std::make_unique<MoveOperation>(bucketId, key._timestamp, std::move(doc),
                                           DbDocumentId(_source->sub_db_id(), key._lid),
                                           _targetSubDbId);
}

void
BucketMover::moveDocument(MoveOperationUP moveOp, IDestructorCallbackSP onDone) {
    _handler->handleMove(*moveOp, std::move(onDone));
}


BucketMover::BucketMover(const BucketId &bucket, const MaintenanceDocumentSubDB *source, uint32_t targetSubDbId,
                         IDocumentMoveHandler &handler, BucketDBOwner &bucketDb) noexcept
    : _source(source),
      _handler(&handler),
      _bucketDb(&bucketDb),
      _bucket(bucket),
      _targetSubDbId(targetSubDbId),
      _bucketDone(false),
      _lastGid(),
      _lastGidValid(false)
{ }

std::pair<std::vector<BucketMover::MoveKey>, bool>
BucketMover::getKeysToMove(size_t maxDocsToMove) const {
    std::pair<std::vector<BucketMover::MoveKey>, bool> result;
    Iterator itr = (_lastGidValid ? _source->meta_store()->upperBound(_lastGid)
                                  : _source->meta_store()->lowerBound(_bucket));
    const Iterator end = _source->meta_store()->upperBound(_bucket);
    std::vector<MoveKey> toMove;
    for (size_t docsMoved(0); itr != end && docsMoved < maxDocsToMove; ++itr) {
        uint32_t lid = itr.getKey().get_lid();
        const RawDocumentMetaData &metaData = _source->meta_store()->getRawMetaData(lid);
        if (metaData.getBucketUsedBits() == _bucket.getUsedBits()) {
            result.first.emplace_back(lid, metaData.getGid(), metaData.getTimestamp());
            ++docsMoved;
        }
    }
    result.second = (itr == end);
    return result;
}

std::vector<MoveOperation::UP>
BucketMover::createMoveOperations(const std::vector<MoveKey> &toMove) {
    std::vector<MoveOperation::UP> successfulReads;
    successfulReads.reserve(toMove.size());
    for (const MoveKey &key : toMove) {
        auto moveOp = createMoveOperation(key);
        if (!moveOp) {
            break;
        }
        successfulReads.push_back(std::move(moveOp));
    }
    return successfulReads;
}

void
BucketMover::moveDocuments(std::vector<MoveOperation::UP> moveOps, IDestructorCallbackSP onDone) {
    for (auto & moveOp : moveOps) {
        moveDocument(std::move(moveOp), onDone);
    }
}

bool
BucketMover::moveDocuments(size_t maxDocsToMove, IMoveOperationLimiter &limiter)
{
    if (_bucketDone) {
        return true;
    }
    auto [keys, done] = getKeysToMove(maxDocsToMove);
    auto moveOps = createMoveOperations(keys);
    bool allOk = keys.size() == moveOps.size();
    if (done && allOk) {
        setBucketDone();
    }
    if (moveOps.empty()) return allOk;

    updateLastValidGid(moveOps.back()->getDocument()->getId().getGlobalId());

    for (auto & moveOp : moveOps) {
        // We cache the bucket for the document we are going to move to avoid getting
        // inconsistent bucket info (getBucketInfo()) while moving between ready and not-ready
        // sub dbs as the bucket info is not updated atomically in this case.
        _bucketDb->takeGuard()->cacheBucket(moveOp->getBucketId());
        _handler->handleMove(*moveOp, limiter.beginOperation());
        _bucketDb->takeGuard()->uncacheBucket();
    }
    return allOk;
}

}

namespace proton {

using bucketdb::BucketMover;

DocumentBucketMover::DocumentBucketMover(IMoveOperationLimiter &limiter) noexcept
    : _limiter(limiter),
      _impl()
{}

void
DocumentBucketMover::setupForBucket(const document::BucketId &bucket, const MaintenanceDocumentSubDB *source,
                                    uint32_t targetSubDbId, IDocumentMoveHandler &handler, bucketdb::BucketDBOwner &bucketDb)
{
    _impl = std::make_unique<BucketMover>(bucket, source, targetSubDbId, handler, bucketDb);
}

bool
DocumentBucketMover::moveDocuments(size_t maxDocsToMove) {
    return !_impl || _impl->moveDocuments(maxDocsToMove, _limiter);
}

}
