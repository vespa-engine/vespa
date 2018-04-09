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

#include "mergestatus.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/storage/storageutil/resumeguard.h>
#include <vespa/storage/common/messagesender.h>

namespace storage {
namespace api {
    class ReturnCode;
    class StorageMessage;
    class StorageCommand;
    class StorageReply;
}
namespace spi {
    class PartitionStateList;
}
namespace framework {
    class HttpUrlPath;
}

class FileStorHandlerImpl;
class FileStorMetrics;
class MessageSender;
class MountPointList;
class ServiceLayerComponentRegister;
class AbortBucketOperationsCommand;

class FileStorHandler : public MessageSender {
public:
    struct RemapInfo {
        document::Bucket bucket;
        uint16_t diskIndex;
        bool foundInQueue;

        RemapInfo(const document::Bucket &bucket_, uint16_t diskIdx)
            : bucket(bucket_),
              diskIndex(diskIdx),
              foundInQueue(false)
            {}
    };

    class BucketLockInterface {
    public:
        typedef std::shared_ptr<BucketLockInterface> SP;

        virtual const document::Bucket &getBucket() const = 0;

        virtual ~BucketLockInterface() {};
    };

    typedef std::pair<BucketLockInterface::SP, api::StorageMessage::SP> LockedMessage;

    enum DiskState {
        AVAILABLE,
        DISABLED,
        CLOSED
    };

    FileStorHandler(uint32_t numStripes, MessageSender&, FileStorMetrics&,
                    const spi::PartitionStateList&, ServiceLayerComponentRegister&);
    FileStorHandler(MessageSender&, FileStorMetrics&,
                    const spi::PartitionStateList&, ServiceLayerComponentRegister&);
    ~FileStorHandler();

        // Commands used by file stor manager

    /**
     * Waits for the filestor queues to be empty. Providing no new load is
     * added while flushing, queues should be empty upon return.
     *
     * @param killPendingMerges If true, clear out all pending merges and reply
     * to them with failure.
     */
    void flush(bool killPendingMerges);

    void setDiskState(uint16_t disk, DiskState state);
    DiskState getDiskState(uint16_t disk);

    /** Check whether a given disk is enabled or not. */
    bool enabled(uint16_t disk) { return (getDiskState(disk) == AVAILABLE); }
    bool closed(uint16_t disk) { return (getDiskState(disk) == CLOSED); }
    /**
     * Disable the given disk. Operations towards threads using this disk will
     * start to fail. Typically called when disk errors are detected.
     */
    void disable(uint16_t disk) { setDiskState(disk, DISABLED); }
    /** Closes all disk threads. */
    void close();

    /**
     * Makes sure no operations are active, then stops any new operations
     * from being performed, until the ResumeGuard is destroyed.
     */
    ResumeGuard pause();

    /**
     * Schedule a storage message to be processed by the given disk
     * @return True if we maanged to schedule operation. False if not
     */
    bool schedule(const std::shared_ptr<api::StorageMessage>&, uint16_t disk);

    /**
     * Used by file stor threads to get their next message to process.
     *
     * @param disk The disk to get messages for
     */
    LockedMessage getNextMessage(uint16_t disk, uint32_t stripeId);

    /**
     * Returns the next message for the same bucket.
     */
    LockedMessage & getNextMessage(uint16_t disk, uint32_t stripeId, LockedMessage& lock);

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
    BucketLockInterface::SP lock(const document::Bucket&, uint16_t disk);

    /**
     * Called by FileStorThread::onBucketDiskMove() after moving file, in case
     * we need to move operations from one disk queue to another.
     *
     * get/put/remove/update/revert/stat/multiop - Move to correct queue
     * merge messages - Move to correct queue. Move any filestor thread state.
     * join/split/getiter/repair/deletebucket - Move to correct queue
     * requeststatus - Ignore
     * readbucketinfo/bucketdiskmove/internalbucketjoin - Fail and log errors
     */
    void remapQueueAfterDiskMove(const document::Bucket &bucket,
                                 uint16_t sourceDisk, uint16_t targetDisk);

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
    void remapQueueAfterJoin(const RemapInfo& source, RemapInfo& target);

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
    void remapQueueAfterSplit(const RemapInfo& source,
                              RemapInfo& target1,
                              RemapInfo& target2);

    struct DeactivateCallback {
        virtual ~DeactivateCallback() {}
        virtual void handleDeactivate() = 0;
    };

    /**
     * Fail all operations towards a single bucket currently queued to the
     * given thread with the given error code.
     */
    void failOperations(const document::Bucket&, uint16_t fromDisk, const api::ReturnCode&);

    /**
     * Add a new merge state to the registry.
     */
    void addMergeStatus(const document::Bucket&, MergeStatus::SP);

    /**
     * Returns the reference to the current merge status for the given bucket.
     * This allows unlocked access to an internal variable, so users should
     * first check that noone else is using it by calling isMerging() first.
     *
     * @param bucket The bucket to start merging.
     */
    MergeStatus& editMergeStatus(const document::Bucket& bucket);

    /**
     * Returns true if the bucket is currently being merged on this node.
     *
     * @param bucket The bucket to check merge status for
     * @return Returns true if the bucket is being merged.
     */
    bool isMerging(const document::Bucket& bucket) const;

    /**
     * @return Returns the number of active merges on the node.
     */
    uint32_t getNumActiveMerges() const;

    /// Provides the next stripe id for a certain disk.
    uint32_t getNextStripeId(uint32_t disk);

    /** Removes the merge status for the given bucket. */
    void clearMergeStatus(const document::Bucket&);
    void clearMergeStatus(const document::Bucket&, const api::ReturnCode&);

    void abortQueuedOperations(const AbortBucketOperationsCommand& cmd);

    /** Send the given command back out of the persistence layer. */
    void sendCommand(const api::StorageCommand::SP&) override;
    /** Send the given reply back out of the persistence layer. */
    void sendReply(const api::StorageReply::SP&) override;

    /** Writes status page. */
    void getStatus(std::ostream& out, const framework::HttpUrlPath& path) const;

    /** Utility function to fetch total size of queue. */
    uint32_t getQueueSize() const;
    uint32_t getQueueSize(uint16_t disk) const;

    // Commands used by testing
    void setGetNextMessageTimeout(uint32_t timeout);

    std::string dumpQueue(uint16_t disk) const;

private:
    FileStorHandlerImpl* _impl;
};

} // storage

