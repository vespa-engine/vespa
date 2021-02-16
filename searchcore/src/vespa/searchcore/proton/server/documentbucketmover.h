// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/base/globalid.h>
#include <persistence/spi/types.h>

namespace vespalib { class IDestructorCallback; }

namespace proton {

struct IDocumentMoveHandler;
struct IMoveOperationLimiter;
class MaintenanceDocumentSubDB;
class MoveOperation;

namespace bucketdb {

class BucketDBOwner;

/**
  * Class used to move all documents in a bucket from a source sub database
  * to a target sub database. The actual moving is handled by a given instance
  * of IDocumentMoveHandler.
  */
class BucketMover
{
public:
    using MoveOperationUP = std::unique_ptr<MoveOperation>;
    using IDestructorCallback = vespalib::IDestructorCallback;
    using IDestructorCallbackSP = std::shared_ptr<IDestructorCallback>;
    struct MoveKey
    {
        using Timestamp = storage::spi::Timestamp;
        MoveKey(uint32_t lid, const document::GlobalId &gid, Timestamp timestamp) noexcept
            : _lid(lid),
              _gid(gid),
              _timestamp(timestamp)
        { }

        uint32_t           _lid;
        document::GlobalId _gid;
        Timestamp          _timestamp;
    };

    BucketMover(const document::BucketId &bucket, const MaintenanceDocumentSubDB *source, uint32_t targetSubDbId,
                IDocumentMoveHandler &handler, BucketDBOwner &bucketDb) noexcept;
    BucketMover(BucketMover &&) noexcept = default;
    BucketMover & operator=(BucketMover &&) noexcept = delete;
    BucketMover(const BucketMover &) = delete;
    BucketMover & operator=(const BucketMover &) = delete;

    // TODO remove once we have switched bucket move job
    bool moveDocuments(size_t maxDocsToMove, IMoveOperationLimiter &limiter);

    /// Must be called in master thread
    std::pair<std::vector<MoveKey>, bool> getKeysToMove(size_t maxDocsToMove) const;
    /// Call from any thread
    std::vector<MoveOperationUP> createMoveOperations(const std::vector<MoveKey> & toMove);
    /// Must be called in master thread
    void moveDocuments(std::vector<MoveOperationUP> moveOps, IDestructorCallbackSP onDone);

    const document::BucketId &getBucket() const { return _bucket; }
    void cancel() { setBucketDone(); }
    void setBucketDone() { _bucketDone = true; }
    bool bucketDone() const { return _bucketDone; }
    const MaintenanceDocumentSubDB * getSource() const { return _source; }
    /// Must be called in master thread
    void updateLastValidGid(const document::GlobalId &gid) {
        _lastGid = gid;
        _lastGidValid = true;
    }
private:
    const MaintenanceDocumentSubDB *_source;
    IDocumentMoveHandler           *_handler;
    BucketDBOwner                  *_bucketDb;
    const document::BucketId        _bucket;
    const uint32_t                  _targetSubDbId;

    bool                            _bucketDone;
    document::GlobalId              _lastGid;
    bool                            _lastGidValid;

    void moveDocument(MoveOperationUP moveOp, IDestructorCallbackSP onDone);
    MoveOperationUP createMoveOperation(const MoveKey & key);
};
}

/**
 * Class used to move all documents in a bucket from a source sub database
 * to a target sub database. The actual moving is handled by a given instance
 * of IDocumentMoveHandler.
 */
class DocumentBucketMover
{
private:
    IMoveOperationLimiter  &_limiter;
    std::unique_ptr<bucketdb::BucketMover> _impl;
public:
    DocumentBucketMover(IMoveOperationLimiter &limiter) noexcept;
    DocumentBucketMover(DocumentBucketMover &&) noexcept = default;
    DocumentBucketMover & operator=(DocumentBucketMover &&) noexcept = delete;
    DocumentBucketMover(const DocumentBucketMover &) = delete;
    DocumentBucketMover & operator=(const DocumentBucketMover &) = delete;
    void setupForBucket(const document::BucketId &bucket,
                        const MaintenanceDocumentSubDB *source,
                        uint32_t targetSubDbId,
                        IDocumentMoveHandler &handler,
                        bucketdb::BucketDBOwner &bucketDb);
    const document::BucketId &getBucket() const { return _impl->getBucket(); }
    bool moveDocuments(size_t maxDocsToMove);
    void cancel() { _impl->cancel(); }
    bool bucketDone() const {
        return !_impl || _impl->bucketDone();
    }
    const MaintenanceDocumentSubDB * getSource() const { return _impl->getSource(); }
};

} // namespace proton

