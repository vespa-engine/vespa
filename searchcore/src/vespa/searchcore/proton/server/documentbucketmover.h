// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/query/base.h>
#include <persistence/spi/types.h>
#include "ifrozenbuckethandler.h"

namespace proton {

class BucketDBOwner;
struct IDocumentMoveHandler;
struct IMoveOperationLimiter;
class MaintenanceDocumentSubDB;

/**
 * Class used to move all documents in a bucket from a source sub database
 * to a target sub database. The actual moving is handled by a given instance
 * of IDocumentMoveHandler.
 */
class DocumentBucketMover
{
private:
    IMoveOperationLimiter          &_limiter;
    document::BucketId              _bucket;
    const MaintenanceDocumentSubDB *_source;
    uint32_t                        _targetSubDbId;
    IDocumentMoveHandler           *_handler;
    BucketDBOwner                  *_bucketDb;
    bool                            _bucketDone;
    document::GlobalId              _lastGid;
    bool                            _lastGidValid;

    void moveDocument(search::DocumentIdT lid,
                      const document::GlobalId &gid,
                      storage::spi::Timestamp timestamp);

    void setBucketDone();
public:
    DocumentBucketMover(IMoveOperationLimiter &limiter);
    void setupForBucket(const document::BucketId &bucket,
                        const MaintenanceDocumentSubDB *source,
                        uint32_t targetSubDbId,
                        IDocumentMoveHandler &handler,
                        BucketDBOwner &bucketDb);
    const document::BucketId &getBucket() const { return _bucket; }
    void moveDocuments(size_t maxDocsToMove);
    void cancel() { setBucketDone(); }
    bool bucketDone() const { return _bucketDone; }
    const MaintenanceDocumentSubDB * getSource() const { return _source; }
};

} // namespace proton

