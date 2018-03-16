// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::FileStorHandlerImpl
 * \ingroup storage
 *
 * \brief Common resource for filestor threads.
 *
 * This class implements all locking related stuff between filestor threads.
 * It keeps the various filestor thread queues, and implement thread safe
 * functions for inserting, removing and moving stuff in the queues. In addition
 * it makes it possible to lock buckets, by keeping track of current operation
 * for various threads, and not allowing them to get another operation of a
 * locked bucket until unlocked.
 */

#pragma once

#include "filestorhandler.h"
#include "mergestatus.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/metrics/metrics.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>
#include <vespa/storage/common/messagesender.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <atomic>

namespace storage {

class FileStorDiskMetrics;
class StorBucketDatabase;
class AbortBucketOperationsCommand;

class FileStorHandlerImpl : private framework::MetricUpdateHook,
                            private ResumeGuard::Callback,
                            public MessageSender {
public:
    typedef FileStorHandler::DiskState DiskState;
    typedef FileStorHandler::RemapInfo RemapInfo;

    struct MessageEntry {
        std::shared_ptr<api::StorageMessage> _command;
        metrics::MetricTimer _timer;
        document::Bucket _bucket;
        uint8_t _priority;

        MessageEntry(const std::shared_ptr<api::StorageMessage>& cmd, const document::Bucket &bId);
        MessageEntry(MessageEntry &&) noexcept ;
        MessageEntry(const MessageEntry &);
        MessageEntry & operator = (const MessageEntry &) = delete;
        ~MessageEntry();

        bool operator<(const MessageEntry& entry) const {
            return (_priority < entry._priority);
        }
    };

    typedef boost::multi_index::ordered_non_unique<
        boost::multi_index::identity<MessageEntry> > PriorityOrder;

    typedef boost::multi_index::ordered_non_unique<
        boost::multi_index::member<MessageEntry,
                                   document::Bucket,
                                   &MessageEntry::_bucket> > BucketOrder;

    typedef boost::multi_index::multi_index_container<
        MessageEntry,
        boost::multi_index::indexed_by<
            boost::multi_index::sequenced<>,
            PriorityOrder,
            BucketOrder
            >
        > PriorityQueue;

    typedef boost::multi_index::nth_index<PriorityQueue, 1>::type PriorityIdx;
    typedef boost::multi_index::nth_index<PriorityQueue, 2>::type BucketIdx;

    struct Disk {
        vespalib::Monitor lock;
        PriorityQueue queue;

        struct LockEntry {
            uint32_t timestamp;
            uint8_t priority;
            vespalib::string statusString;

            LockEntry()
                : timestamp(0), priority(0), statusString()
            { }

            LockEntry(uint8_t priority_, vespalib::stringref status)
                : timestamp(time(NULL)),
                  priority(priority_),
                  statusString(status)
            { }
        };

        typedef vespalib::hash_map<document::Bucket, LockEntry, document::Bucket::hash> LockedBuckets;
        LockedBuckets lockedBuckets;
        FileStorDiskMetrics* metrics;

        /**
         * No assumption on memory ordering around disk state reads should
         * be made by callers.
         */
        DiskState getState() const noexcept {
            return state.load(std::memory_order_relaxed);
        }
        /**
         * No assumption on memory ordering around disk state writes should
         * be made by callers.
         */
        void setState(DiskState s) noexcept {
            state.store(s, std::memory_order_relaxed);
        }

        Disk();
        ~Disk();

        bool isLocked(const document::Bucket&) const noexcept;
        uint32_t getQueueSize() const noexcept;
    private:
        std::atomic<DiskState> state;
    };

    class BucketLock : public FileStorHandler::BucketLockInterface {
    public:
        BucketLock(const vespalib::MonitorGuard & guard, Disk& disk, const document::Bucket &bucket, uint8_t priority,
                   const vespalib::stringref & statusString);
        ~BucketLock();

        const document::Bucket &getBucket() const override { return _bucket; }

    private:
        Disk& _disk;
        document::Bucket _bucket;
    };

    FileStorHandlerImpl(MessageSender&,
                        FileStorMetrics&,
                        const spi::PartitionStateList&,
                        ServiceLayerComponentRegister&,
                        uint8_t maxPriorityToBlock,
                        uint8_t minPriorityToBeBlocking);

    ~FileStorHandlerImpl();
    void setGetNextMessageTimeout(uint32_t timeout) { _getNextMessageTimeout = timeout; }

    void flush(bool killPendingMerges);
    void setDiskState(uint16_t disk, DiskState state);
    DiskState getDiskState(uint16_t disk) const;
    void close();
    bool schedule(const std::shared_ptr<api::StorageMessage>&, uint16_t disk);

    void pause(uint16_t disk, uint8_t priority) const;
    FileStorHandler::LockedMessage getNextMessage(uint16_t disk);
    FileStorHandler::LockedMessage getMessage(vespalib::MonitorGuard & guard, Disk & t, PriorityIdx & idx, PriorityIdx::iterator iter);

    FileStorHandler::LockedMessage & getNextMessage(uint16_t disk, FileStorHandler::LockedMessage& lock);

    enum Operation { MOVE, SPLIT, JOIN };
    void remapQueue(const RemapInfo& source, RemapInfo& target, Operation op);

    void remapQueue(const RemapInfo& source, RemapInfo& target1, RemapInfo& target2, Operation op);

    void failOperations(const document::Bucket&, uint16_t fromDisk, const api::ReturnCode&);
    void sendCommand(const std::shared_ptr<api::StorageCommand>&) override;
    void sendReply(const std::shared_ptr<api::StorageReply>&) override;

    void getStatus(std::ostream& out, const framework::HttpUrlPath& path) const;

    uint32_t getQueueSize() const;
    uint32_t getQueueSize(uint16_t disk) const;

    std::shared_ptr<FileStorHandler::BucketLockInterface>
    lock(const document::Bucket&, uint16_t disk);

    void addMergeStatus(const document::Bucket&, MergeStatus::SP);
    MergeStatus& editMergeStatus(const document::Bucket&);
    bool isMerging(const document::Bucket&) const;
    uint32_t getNumActiveMerges() const;
    void clearMergeStatus(const document::Bucket&, const api::ReturnCode*);

    std::string dumpQueue(uint16_t disk) const;
    ResumeGuard pause();
    void resume() override;
    void abortQueuedOperations(const AbortBucketOperationsCommand& cmd);

private:
    const spi::PartitionStateList& _partitions;
    ServiceLayerComponent _component;
    std::vector<Disk> _diskInfo;
    MessageSender& _messageSender;
    const document::BucketIdFactory& _bucketIdFactory;

    vespalib::Lock _mergeStatesLock;

    std::map<document::Bucket, MergeStatus::SP> _mergeStates;

    uint8_t _maxPriorityToBlock;
    uint8_t _minPriorityToBeBlocking;
    uint32_t _getNextMessageTimeout;

    vespalib::Monitor _pauseMonitor;
    std::atomic<bool> _paused;

    void reply(api::StorageMessage&, DiskState state) const;

    // Returns the index in the targets array we are sending to, or -1 if none of them match.
    int calculateTargetBasedOnDocId(const api::StorageMessage& msg, std::vector<RemapInfo*>& targets);

    /**
     * If FileStor layer is explicitly paused, try to wait a single time, then
     * recheck pause status. Returns true if filestor isn't paused at the time
     * of the first check or after the wait, false if it's still paused.
     */
    bool tryHandlePause(uint16_t disk) const;

    /**
     * Checks whether the entire filestor layer is paused.
     * Since there should be no data or synchronization dependencies on
     * _paused, use relaxed atomics.
     */
    bool isPaused() const { return _paused.load(std::memory_order_relaxed); }

    /**
     * Return whether a disk has been shut down by the system (IO failure is
     * the most likely candidate here) and should not serve any more requests.
     */
    bool diskIsClosed(uint16_t disk) const;

    /**
     * Return whether an already running high priority operation pre-empts
     * (blocks) the operation in msg from even starting in the current thread.
     */
    bool operationBlockedByHigherPriorityThread(const api::StorageMessage& msg, const Disk& disk) const;

    /**
     * Return whether msg has timed out based on waitTime and the message's
     * specified timeout.
     */
    bool messageTimedOutInQueue(const api::StorageMessage& msg, uint64_t waitTime) const;

    /**
     * Assume ownership of lock for a given bucket on a given disk.
     * Disk lock MUST have been taken prior to calling this function.
     */
    std::unique_ptr<FileStorHandler::BucketLockInterface>
    takeDiskBucketLockOwnership(const vespalib::MonitorGuard & guard,
                                Disk& disk, const document::Bucket &bucket, const api::StorageMessage& msg);

    /**
     * Creates and returns a reply with api::TIMEOUT return code for msg.
     * Swaps (invalidates) context from msg into reply.
     */
    std::unique_ptr<api::StorageReply> makeQueueTimeoutReply(api::StorageMessage& msg) const;
    bool messageMayBeAborted(const api::StorageMessage& msg) const;
    bool hasBlockingOperations(const Disk& t) const;
    void abortQueuedCommandsForBuckets(Disk& disk, const AbortBucketOperationsCommand& cmd);
    bool diskHasActiveOperationForAbortedBucket(const Disk& disk, const AbortBucketOperationsCommand& cmd) const;
    void waitUntilNoActiveOperationsForAbortedBuckets(Disk& disk, const AbortBucketOperationsCommand& cmd);

    // Update hook
    void updateMetrics(const MetricLockGuard &) override;

    document::Bucket remapMessage(api::StorageMessage& msg,
                                  const document::Bucket &source,
                                  Operation op,
                                  std::vector<RemapInfo*>& targets,
                                  uint16_t& targetDisk,
                                  api::ReturnCode& returnCode);

    void remapQueueNoLock(Disk& from, const RemapInfo& source, std::vector<RemapInfo*>& targets, Operation op);

    /**
     * Waits until the queue has no pending operations (i.e. no locks are
     * being held.
     */
    void waitUntilNoLocks();
};

} // storage

