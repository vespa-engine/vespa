// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/persistence/spi/types.h>
#include <atomic>

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
class BucketMover : public std::enable_shared_from_this<BucketMover>
{
public:
    using MoveOperationUP = std::unique_ptr<MoveOperation>;
    using IDestructorCallback = vespalib::IDestructorCallback;
    using IDestructorCallbackSP = std::shared_ptr<IDestructorCallback>;
    class MoveGuard {
    public:
        MoveGuard() noexcept : _mover(nullptr) {}
        MoveGuard(BucketMover & mover) noexcept
            : _mover(&mover)
        {
            _mover->_started.fetch_add(1, std::memory_order_relaxed);
        }
        MoveGuard(MoveGuard && rhs) noexcept : _mover(rhs._mover) { rhs._mover = nullptr; }
        MoveGuard & operator = (MoveGuard && mover) = delete;
        MoveGuard(const MoveGuard & rhs) = delete;
        MoveGuard & operator = (const MoveGuard & mover) = delete;
        ~MoveGuard() {
            if (_mover) {
                _mover->_completed.fetch_add(1, std::memory_order_relaxed);
            }
        }
    private:
        BucketMover *_mover;
    };
    struct MoveKey
    {
        using Timestamp = storage::spi::Timestamp;
        MoveKey(uint32_t lid, const document::GlobalId &gid, Timestamp timestamp, MoveGuard guard) noexcept;
        MoveKey(MoveKey &&) noexcept = default;
        ~MoveKey();

        uint32_t           _lid;
        document::GlobalId _gid;
        Timestamp          _timestamp;
        MoveGuard          _guard;
    };

    using GuardedMoveOp = std::pair<MoveOperationUP, MoveGuard>;
    class GuardedMoveOps {
    public:
        GuardedMoveOps(std::shared_ptr<BucketMover> mover) noexcept;
        GuardedMoveOps(GuardedMoveOps &&) = default;
        GuardedMoveOps & operator =(GuardedMoveOps &&) = default;
        GuardedMoveOps(const GuardedMoveOps &) = delete;
        GuardedMoveOps & operator = (const GuardedMoveOps &) = delete;
        ~GuardedMoveOps();
        std::vector<GuardedMoveOp> & success() { return _success; }
        std::vector<MoveGuard> & failed() { return _failed; }
        BucketMover & mover() { return *_mover; }
    private:
        // It is important to keep the order so the mover is destructed last
        std::shared_ptr<BucketMover> _mover;
        std::vector<GuardedMoveOp> _success;
        std::vector<MoveGuard> _failed;
    };

    class MoveKeys {
    public:
        MoveKeys(std::shared_ptr<BucketMover> mover) noexcept : _mover(std::move(mover)) {}
        MoveKeys(MoveKeys &&) noexcept = default;
        MoveKeys & operator =(MoveKeys &&) noexcept = default;
        MoveKeys(const MoveKeys &) noexcept = delete;
        MoveKeys & operator =(const MoveKeys &) noexcept = delete;
        ~MoveKeys();
        GuardedMoveOps createMoveOperations();
        std::shared_ptr<BucketMover> stealMover();
        std::vector<MoveKey> & keys() { return _keys; }
        size_t size() const { return _keys.size(); }
        bool empty() const { return _keys.empty(); }
        const MoveKey & back() const { return _keys.back(); }
        const BucketMover & mover() const { return *_mover; }
    private:
        // It is important to keep the order so the mover is destructed last
        std::shared_ptr<BucketMover> _mover;
        std::vector<MoveKey> _keys;
    };

    static std::shared_ptr<BucketMover>
    create(const document::BucketId &bucket, const MaintenanceDocumentSubDB *source,
           uint32_t targetSubDbId, IDocumentMoveHandler &handler);
    BucketMover(BucketMover &&) noexcept = delete;
    BucketMover & operator=(BucketMover &&) noexcept = delete;
    BucketMover(const BucketMover &) = delete;
    BucketMover & operator=(const BucketMover &) = delete;
    ~BucketMover();

    /// Must be called in master thread
    std::pair<MoveKeys, bool> getKeysToMove(size_t maxDocsToMove);
    /// Call from any thread
    GuardedMoveOps createMoveOperations(MoveKeys toMove);
    /// Must be called in master thread
    void moveDocuments(std::vector<GuardedMoveOp> moveOps, IDestructorCallbackSP onDone);
    void moveDocument(MoveOperationUP moveOp, IDestructorCallbackSP onDone);

    const document::BucketId &getBucket() const { return _bucket; }
    void cancel();
    [[nodiscard]] bool cancelled() const noexcept { return _cancelled; }
    void setAllScheduled() { _allScheduled = true; }
    /// Signals all documents have been scheduled for move
    bool allScheduled() const { return _allScheduled; }
    bool needReschedule() const { return _needReschedule.load(std::memory_order_relaxed); }
    const MaintenanceDocumentSubDB * getSource() const { return _source; }
    /// Must be called in master thread
    void updateLastValidGid(const document::GlobalId &gid) {
        _lastGid = gid;
        _lastGidValid = true;
    }
    bool inSync() const {
        return pending() == 0;
    }
private:
    BucketMover(const document::BucketId &bucket, const MaintenanceDocumentSubDB *source,
                uint32_t targetSubDbId, IDocumentMoveHandler &handler) noexcept;
    const MaintenanceDocumentSubDB *_source;
    IDocumentMoveHandler           *_handler;
    const document::BucketId        _bucket;
    const uint32_t                  _targetSubDbId;

    std::atomic<uint32_t>           _started;
    std::atomic<uint32_t>           _completed;
    std::atomic<bool>               _needReschedule;
    bool                            _cancelled;
    bool                            _allScheduled; // All moves started, or operation has been cancelled
    bool                            _lastGidValid;
    document::GlobalId              _lastGid;
    MoveOperationUP createMoveOperation(const MoveKey & key);
    size_t pending() const {
        return _started.load(std::memory_order_relaxed) - _completed.load(std::memory_order_relaxed);
    }
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
    IMoveOperationLimiter                  &_limiter;
    bucketdb::BucketDBOwner                *_bucketDb;
    std::shared_ptr<bucketdb::BucketMover>  _impl;

    bool moveDocuments(size_t maxDocsToMove, IMoveOperationLimiter &limiter);
public:
    DocumentBucketMover(IMoveOperationLimiter &limiter, bucketdb::BucketDBOwner &bucketDb) noexcept;
    DocumentBucketMover(DocumentBucketMover &&) noexcept = default;
    DocumentBucketMover & operator=(DocumentBucketMover &&) noexcept = delete;
    DocumentBucketMover(const DocumentBucketMover &) = delete;
    DocumentBucketMover & operator=(const DocumentBucketMover &) = delete;
    void setupForBucket(const document::BucketId &bucket,
                        const MaintenanceDocumentSubDB *source,
                        uint32_t targetSubDbId,
                        IDocumentMoveHandler &handler);
    const document::BucketId &getBucket() const { return _impl->getBucket(); }
    bool moveDocuments(size_t maxDocsToMove);
    void cancel() { _impl->cancel(); }
    bool needReschedule() { return _impl && _impl->needReschedule(); }
    bool bucketDone() const {
        return !_impl || _impl->allScheduled();
    }
    const MaintenanceDocumentSubDB * getSource() const { return _impl->getSource(); }
};

} // namespace proton

