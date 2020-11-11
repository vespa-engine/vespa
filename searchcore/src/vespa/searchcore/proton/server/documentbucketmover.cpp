// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentbucketmover.h"
#include "i_move_operation_limiter.h"
#include "idocumentmovehandler.h"
#include "maintenancedocumentsubdb.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/document/fieldvalue/document.h>

using document::BucketId;
using document::Document;
using document::GlobalId;
using search::DocumentIdT;
using storage::spi::Timestamp;

namespace proton {

typedef IDocumentMetaStore::Iterator Iterator;

bool
DocumentBucketMover::moveDocument(DocumentIdT lid,
                                  const document::GlobalId &gid,
                                  Timestamp timestamp)
{
    if ( _source->lidNeedsCommit(lid) ) {
        return false;
    }
    Document::SP doc(_source->retriever()->getFullDocument(lid));
    if (!doc || doc->getId().getGlobalId() != gid)
        return true; // Failed to retrieve document, removed or changed identity
    // TODO(geirst): what if doc is NULL?
    BucketId bucketId = _bucket.stripUnused();
    MoveOperation op(bucketId, timestamp, doc, DbDocumentId(_source->sub_db_id(), lid), _targetSubDbId);

    // We cache the bucket for the document we are going to move to avoid getting
    // inconsistent bucket info (getBucketInfo()) while moving between ready and not-ready
    // sub dbs as the bucket info is not updated atomically in this case.
    _bucketDb->takeGuard()->cacheBucket(bucketId);
    _handler->handleMove(op, _limiter.beginOperation());
    _bucketDb->takeGuard()->uncacheBucket();
    return true;
}


DocumentBucketMover::DocumentBucketMover(IMoveOperationLimiter &limiter)
    : _limiter(limiter),
      _bucket(),
      _source(nullptr),
      _targetSubDbId(0),
      _handler(nullptr),
      _bucketDb(nullptr),
      _bucketDone(true),
      _lastGid(),
      _lastGidValid(false)
{ }


void
DocumentBucketMover::setupForBucket(const BucketId &bucket,
                                    const MaintenanceDocumentSubDB *source,
                                    uint32_t targetSubDbId,
                                    IDocumentMoveHandler &handler,
                                    BucketDBOwner &bucketDb)
{
    _bucket = bucket;
    _source = source;
    _targetSubDbId = targetSubDbId;
    _handler = &handler;
    _bucketDb = &bucketDb;
    _bucketDone = false;
    _lastGid = GlobalId();
    _lastGidValid = false;
}


namespace
{

class MoveKey
{
public:
    DocumentIdT _lid;
    document::GlobalId _gid;
    Timestamp _timestamp;

    MoveKey(DocumentIdT lid,
            const document::GlobalId &gid,
            Timestamp timestamp)
        : _lid(lid),
          _gid(gid),
          _timestamp(timestamp)
    {
    }
};

}

void
DocumentBucketMover::setBucketDone() {
    _bucketDone = true;
}

bool
DocumentBucketMover::moveDocuments(size_t maxDocsToMove)
{
    if (_bucketDone) {
        return true;
    }
    Iterator itr = (_lastGidValid ? _source->meta_store()->upperBound(_lastGid)
                    : _source->meta_store()->lowerBound(_bucket));
    const Iterator end = _source->meta_store()->upperBound(_bucket);
    size_t docsMoved = 0;
    size_t docsSkipped = 0; // In absence of a proper cost metric
    typedef std::vector<MoveKey> MoveVec;
    MoveVec toMove;
    for (; itr != end && docsMoved < maxDocsToMove; ++itr) {
        DocumentIdT lid = itr.getKey().get_lid();
        const RawDocumentMetaData &metaData = _source->meta_store()->getRawMetaData(lid);
        if (metaData.getBucketUsedBits() != _bucket.getUsedBits()) {
            ++docsSkipped;
            if (docsSkipped >= 50) {
                ++docsMoved; // In absence of a proper cost metric
                docsSkipped = 0;
            }
        } else {
            // moveDocument(lid, metaData.getTimestamp());
            toMove.push_back(MoveKey(lid, metaData.getGid(), metaData.getTimestamp()));
            ++docsMoved;
        }
    }
    bool done = (itr == end);
    for (const MoveKey & key : toMove) {
        if ( ! moveDocument(key._lid, key._gid, key._timestamp)) {
            return false;
        }
        _lastGid = key._gid;
        _lastGidValid = true;
    }
    if (done) {
        setBucketDone();
    }
    return true;
}


} // namespace proton
