// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class storage::FileStorHandler
 * \ingroup storage
 *
 * \brief Common resource for filestor threads
 *
 * Takes care of the interface between file stor threads and the file stor
 * manager to avoid circular dependencies, and confine the implementation that
 * needs to worry about locking between these components.
 */

#pragma once

#include <vespa/document/bucket/bucket.h>
#include <vespa/storage/storageutil/resumeguard.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storageapi/messageapi/storagemessage.h>

namespace storage {
namespace api {
    class ReturnCode;
    class StorageMessage;
    class StorageCommand;
    class StorageReply;
}
namespace framework {
    class HttpUrlPath;
}

class FileStorHandlerImpl;
struct FileStorMetrics;
struct MessageSender;
struct ServiceLayerComponentRegister;
class AbortBucketOperationsCommand;
class MergeStatus;

class FileStorHandler : public MessageSender {
public:
    struct RemapInfo {
        document::Bucket bucket;
        bool foundInQueue;

        RemapInfo(const document::Bucket &bucket_)
            : bucket(bucket_),
              foundInQueue(false)
        {}
    };

    class BucketLockInterface {
    public:
        using SP = std::shared_ptr<BucketLockInterface>;

        virtual const document::Bucket &getBucket() const = 0;
        virtual api::LockingRequirements lockingRequirements() const noexcept = 0;

        virtual ~BucketLockInterface() = default;
    };

    using LockedMessage = std::pair<BucketLockInterface::SP, api::StorageMessage::SP>;
    class ScheduleAsyncResult {
    private:
        bool _was_scheduled;
        LockedMessage _async_message;

    public:
        ScheduleAsyncResult() : _was_scheduled(false), _async_message() {}
        explicit ScheduleAsyncResult(LockedMessage&& async_message_in)
            : _was_scheduled(true),
              _async_message(std::move(async_message_in))
        {}
        bool was_scheduled() const {
            return _was_scheduled;
        }
        bool has_async_message() const {
            return _async_message.first.get() != nullptr;
        }
        const LockedMessage& async_message() const {
            return _async_message;
        }
        LockedMessage&& release_async_message() {
            return std::move(_async_message);
        }
    };

    enum DiskState {
        AVAILABLE,
        CLOSED
    };

    virtual ~FileStorHandler() = default;


    /**
     * Waits for the filestor queues to be empty. Providing no new load is
     * added while flushing, queues should be empty upon return.
     *
     * @param killPendingMerges If true, clear out all pending merges and reply
     * to them with failure.
     */
    virtual void flush(bool killPendingMerges) = 0;

    virtual void setDiskState(DiskState state) = 0;
    virtual DiskState getDiskState() const = 0;

    /** Check if it has been closed. */
    bool closed() const { return (getDiskState() == CLOSED); }
    /** Closes all disk threads. */
    virtual void close() = 0;

    /**
     * Makes sure no operations are active, then stops any new operations
     * from being performed, until the ResumeGuard is destroyed.
     */
    virtual ResumeGuard pause() = 0;

    /**
     * Schedule a storage message to be processed
     * @return True if we maanged to schedule operation. False if not
     */
    virtual bool schedule(const std::shared_ptr<api::StorageMessage>&) = 0;

    /**
     * Schedule the given message to be processed and return the next async message to process (if any).
     */
    virtual ScheduleAsyncResult schedule_and_get_next_async_message(const std::shared_ptr<api::StorageMessage>& msg) = 0;

    /**
     * Used by file stor threads to get their next message to process.
     *
     * @param stripe The stripe to get messages for
     */
    virtual LockedMessage getNextMessage(uint32_t stripeId) = 0;

    /**
     * Lock a bucket. By default, each file stor thread has the locks of all
     * buckets in their area of responsibility. If they need to access buckets
     * outside of their area, they can call this to make sure the thread
     * responsible for it doesn't interfere during the operation.
     * This function will block until bucket is locked, and an operation on it
     * is not pending. (Handler tracks current operation by remembering bucket
     * of last message taken for each thread)
     * NB: As current operation can be a split or join operation, make sure that
     * you always wait for current to finish, if is a super or sub bucket of
     * the bucket we're locking.
     *
     *
     */
    virtual BucketLockInterface::SP lock(const document::Bucket&, api::LockingRequirements lockReq) = 0;

    /**
     * Called by FileStorThread::onJoin() after joining a bucket into another,
     * in case we need to move operations from one disk queue to another, and
     * to remap operations to contain correct bucket target.
     * Merge operations towards removed bucket probably needs to be aborted,
     * so we remove any merge state stored in the filestor thread.
     *
     * get/put/remove/update/revert/multiop - Move to correct queue
     * stat - Fail with bucket not found
     * merge messages - Fail with bucket not found. Erase merge state in thread.
     * join - Ignore
     * split/getiter/repair/bucketdiskmove - Fail with bucket not found
     * requeststatus/deletebucket - Ignore
     * readbucketinfo/internalbucketjoin - Fail and log errors
     */
    virtual void remapQueueAfterJoin(const RemapInfo& source, RemapInfo& target) = 0;

    /**
     * Called by FileStorThread::onSplit() after splitting a bucket,
     * in case we need to move operations from one disk queue to another, and
     * to remap operations to contain correct bucket target.
     * Merge operations towards removed bucket probably needs to be aborted,
     * so we remove any merge state stored in the filestor thread.
     * Split targets that wasn't created sets bucket raw id to 0 to indicate
     * that they were not added.
     *
     * get/put/remove/update/revert - Move to correct queue
     * revert - In some way revert on both or correct copy
     * multiop/stat - Fail with bucket not found
     * merge messages - Fail with bucket not found. Erase merge state in thread.
     * join - Ignore
     * split/getiter/repair/bucketdiskmove - Fail with bucket not found
     * requeststatus/deletebucket - Ignore
     * readbucketinfo/internalbucketjoin - Fail and log errors
     */
    virtual void remapQueueAfterSplit(const RemapInfo& source,
                                      RemapInfo& target1,
                                      RemapInfo& target2) = 0;

    /**
     * Fail all operations towards a single bucket currently queued to the
     * given thread with the given error code.
     */
    virtual void failOperations(const document::Bucket&, const api::ReturnCode&) = 0;

    /**
     * Add a new merge state to the registry.
     */
    virtual void addMergeStatus(const document::Bucket&, std::shared_ptr<MergeStatus>) = 0;

    /**
     * Returns the reference to the current merge status for the given bucket.
     * This allows unlocked access to an internal variable, so users should
     * first check that noone else is using it by calling isMerging() first.
     *
     * @param bucket The bucket to start merging.
     */
    virtual MergeStatus& editMergeStatus(const document::Bucket& bucket) = 0;

    /**
     * Returns true if the bucket is currently being merged on this node.
     *
     * @param bucket The bucket to check merge status for
     * @return Returns true if the bucket is being merged.
     */
    virtual bool isMerging(const document::Bucket& bucket) const = 0;

    /** Removes the merge status for the given bucket. */
    virtual void clearMergeStatus(const document::Bucket&) = 0;
    virtual void clearMergeStatus(const document::Bucket&, const api::ReturnCode&) = 0;

    virtual void abortQueuedOperations(const AbortBucketOperationsCommand& cmd) = 0;

    /** Writes status page. */
    virtual void getStatus(std::ostream& out, const framework::HttpUrlPath& path) const = 0;

    /** Utility function to fetch total size of queue. */
    virtual uint32_t getQueueSize() const = 0;

    // Commands used by testing
    virtual void setGetNextMessageTimeout(vespalib::duration timeout) = 0;

    virtual std::string dumpQueue() const = 0;

};

} // storage

