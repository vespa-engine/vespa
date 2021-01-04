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
#include <vespa/document/bucket/bucketid.h>
#include <vespa/metrics/metrictimer.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>
#include <vespa/storage/common/messagesender.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <atomic>
#include <optional>

namespace storage {

class FileStorDiskMetrics;
class FileStorStripeMetrics;
class StorBucketDatabase;
class AbortBucketOperationsCommand;

namespace bmi = boost::multi_index;

class FileStorHandlerImpl : private framework::MetricUpdateHook,
                            private ResumeGuard::Callback,
                            public FileStorHandler {
public:

    struct MessageEntry {
        std::shared_ptr<api::StorageMessage> _command;
        metrics::MetricTimer _timer;
        document::Bucket _bucket;
        uint8_t _priority;

        MessageEntry(const std::shared_ptr<api::StorageMessage>& cmd, const document::Bucket &bId);
        MessageEntry(MessageEntry &&) noexcept ;
        MessageEntry(const MessageEntry &) noexcept;
        MessageEntry & operator = (const MessageEntry &) = delete;
        ~MessageEntry();

        bool operator<(const MessageEntry& entry) const {
            return (_priority < entry._priority);
        }
    };

    using PriorityOrder = bmi::ordered_non_unique<bmi::identity<MessageEntry> >;
    using BucketOrder = bmi::ordered_non_unique<bmi::member<MessageEntry, document::Bucket, &MessageEntry::_bucket>>;

    using PriorityQueue = bmi::multi_index_container<MessageEntry, bmi::indexed_by<bmi::sequenced<>, PriorityOrder, BucketOrder>>;

    using PriorityIdx = bmi::nth_index<PriorityQueue, 1>::type;
    using BucketIdx = bmi::nth_index<PriorityQueue, 2>::type;
    using Clock = std::chrono::steady_clock;
    using monitor_guard = std::unique_lock<std::mutex>;

    class Stripe {
    public:
        struct LockEntry {
            Clock::time_point       timestamp;
            uint8_t                 priority;
            api::MessageType::Id    msgType;
            api::StorageMessage::Id msgId;

            LockEntry() noexcept : timestamp(), priority(0), msgType(), msgId(0) { }

            LockEntry(uint8_t priority_, api::MessageType::Id msgType_, api::StorageMessage::Id msgId_) noexcept
                : timestamp(Clock::now()), priority(priority_), msgType(msgType_), msgId(msgId_)
            { }
        };

        struct MultiLockEntry {
            std::optional<LockEntry> _exclusiveLock;
            using SharedLocks = vespalib::hash_map<api::StorageMessage::Id, LockEntry>;
            SharedLocks _sharedLocks;
        };

        Stripe(const FileStorHandlerImpl & owner, MessageSender & messageSender);
        Stripe(Stripe &&) noexcept;
        Stripe(const Stripe &) = delete;
        Stripe & operator =(const Stripe &) = delete;
        ~Stripe();
        void flush();
        bool schedule(MessageEntry messageEntry);
        FileStorHandler::LockedMessage schedule_and_get_next_async_message(MessageEntry entry);
        void waitUntilNoLocks() const;
        void abort(std::vector<std::shared_ptr<api::StorageReply>> & aborted, const AbortBucketOperationsCommand& cmd);
        void waitInactive(const AbortBucketOperationsCommand& cmd) const;

        void broadcast() {
            _cond->notify_all();
        }
        size_t getQueueSize() const {
            std::lock_guard guard(*_lock);
            return _queue->size();
        }
        void release(const document::Bucket & bucket, api::LockingRequirements reqOfReleasedLock,
                     api::StorageMessage::Id lockMsgId);

        // Subsumes isLocked
        bool operationIsInhibited(const monitor_guard &, const document::Bucket&,
                                  const api::StorageMessage&) const noexcept;
        bool isLocked(const monitor_guard &, const document::Bucket&,
                      api::LockingRequirements lockReq) const noexcept;

        void lock(const monitor_guard &, const document::Bucket & bucket,
                  api::LockingRequirements lockReq, const LockEntry & lockEntry);

        std::shared_ptr<FileStorHandler::BucketLockInterface> lock(const document::Bucket & bucket, api::LockingRequirements lockReq);
        void failOperations(const document::Bucket & bucket, const api::ReturnCode & code);

        FileStorHandler::LockedMessage getNextMessage(vespalib::duration timeout);
        void dumpQueue(std::ostream & os) const;
        void dumpActiveHtml(std::ostream & os) const;
        void dumpQueueHtml(std::ostream & os) const;
        std::mutex & exposeLock() { return *_lock; }
        PriorityQueue & exposeQueue() { return *_queue; }
        BucketIdx & exposeBucketIdx() { return bmi::get<2>(*_queue); }
        void setMetrics(FileStorStripeMetrics * metrics) { _metrics = metrics; }
    private:
        bool hasActive(monitor_guard & monitor, const AbortBucketOperationsCommand& cmd) const;
        FileStorHandler::LockedMessage get_next_async_message(monitor_guard& guard);

        // Precondition: the bucket used by `iter`s operation is not locked in a way that conflicts
        // with its locking requirements.
        FileStorHandler::LockedMessage getMessage(monitor_guard & guard, PriorityIdx & idx,
                                                  PriorityIdx::iterator iter);
        using LockedBuckets = vespalib::hash_map<document::Bucket, MultiLockEntry, document::Bucket::hash>;
        const FileStorHandlerImpl      &_owner;
        MessageSender                  &_messageSender;
        FileStorStripeMetrics          *_metrics;
        std::unique_ptr<std::mutex>                _lock;
        std::unique_ptr<std::condition_variable>   _cond;
        std::unique_ptr<PriorityQueue>  _queue;
        LockedBuckets                   _lockedBuckets;
        uint32_t                        _active_merges;
    };

    class BucketLock : public FileStorHandler::BucketLockInterface {
    public:
        // TODO refactor, too many params
        BucketLock(const monitor_guard & guard, Stripe& disk, const document::Bucket &bucket,
                   uint8_t priority, api::MessageType::Id msgType, api::StorageMessage::Id,
                   api::LockingRequirements lockReq);
        ~BucketLock() override;

        const document::Bucket &getBucket() const override { return _bucket; }
        api::LockingRequirements lockingRequirements() const noexcept override { return _lockReq; }

    private:
        Stripe                 & _stripe;
        document::Bucket         _bucket;
        api::StorageMessage::Id  _uniqueMsgId;
        api::LockingRequirements _lockReq;
    };


    FileStorHandlerImpl(MessageSender& sender, FileStorMetrics& metrics,
                        ServiceLayerComponentRegister& compReg);
    FileStorHandlerImpl(uint32_t numThreads, uint32_t numStripes, MessageSender&, FileStorMetrics&,
                        ServiceLayerComponentRegister&);

    ~FileStorHandlerImpl();
    void setGetNextMessageTimeout(vespalib::duration timeout) override { _getNextMessageTimeout = timeout; }

    void flush(bool killPendingMerges) override;
    void setDiskState(DiskState state) override;
    DiskState getDiskState() const override;
    void close() override;
    bool schedule(const std::shared_ptr<api::StorageMessage>&) override;
    ScheduleAsyncResult schedule_and_get_next_async_message(const std::shared_ptr<api::StorageMessage>& msg) override;

    FileStorHandler::LockedMessage getNextMessage(uint32_t stripeId) override;

    void remapQueueAfterJoin(const RemapInfo& source, RemapInfo& target) override;
    void remapQueueAfterSplit(const RemapInfo& source, RemapInfo& target1, RemapInfo& target2) override;

    enum Operation { MOVE, SPLIT, JOIN };
    void remapQueue(const RemapInfo& source, RemapInfo& target, Operation op);

    void remapQueue(const RemapInfo& source, RemapInfo& target1, RemapInfo& target2, Operation op);

    void failOperations(const document::Bucket & bucket, const api::ReturnCode & code) override {
        stripe(bucket).failOperations(bucket, code);
    }

    // Implements MessageSender
    void sendCommand(const std::shared_ptr<api::StorageCommand>&) override;
    void sendReply(const std::shared_ptr<api::StorageReply>&) override;
    void sendReplyDirectly(const api::StorageReply::SP& msg) override;

    void getStatus(std::ostream& out, const framework::HttpUrlPath& path) const override;

    uint32_t getQueueSize() const override;

    std::shared_ptr<FileStorHandler::BucketLockInterface>
    lock(const document::Bucket & bucket, api::LockingRequirements lockReq) override {
        return stripe(bucket).lock(bucket, lockReq);
    }

    void addMergeStatus(const document::Bucket&, std::shared_ptr<MergeStatus>) override;
    MergeStatus& editMergeStatus(const document::Bucket&) override;
    bool isMerging(const document::Bucket&) const override;
    void clearMergeStatus(const document::Bucket& bucket) override;
    void clearMergeStatus(const document::Bucket& bucket, const api::ReturnCode& code) override;

    void clearMergeStatus(const document::Bucket&, const api::ReturnCode*);

    std::string dumpQueue() const override;
    ResumeGuard pause() override;
    void abortQueuedOperations(const AbortBucketOperationsCommand& cmd) override;

    // Implements ResumeGuard::Callback
    void resume() override;

private:
    ServiceLayerComponent   _component;
    std::atomic<DiskState>  _state;
    FileStorDiskMetrics   * _metrics;
    std::vector<Stripe>     _stripes;
    MessageSender&          _messageSender;
    const document::BucketIdFactory& _bucketIdFactory;
    mutable std::mutex    _mergeStatesLock;
    std::map<document::Bucket, std::shared_ptr<MergeStatus>> _mergeStates;
    vespalib::duration    _getNextMessageTimeout;
    const uint32_t        _max_active_merges_per_stripe; // Read concurrently by stripes.
    mutable std::mutex              _pauseMonitor;
    mutable std::condition_variable _pauseCond;
    std::atomic<bool>               _paused;

    // Returns the index in the targets array we are sending to, or -1 if none of them match.
    int calculateTargetBasedOnDocId(const api::StorageMessage& msg, std::vector<RemapInfo*>& targets);

    /**
     * If FileStor layer is explicitly paused, try to wait a single time, then
     * recheck pause status. Returns true if filestor isn't paused at the time
     * of the first check or after the wait, false if it's still paused.
     */
    bool tryHandlePause() const;

    /**
     * Checks whether the entire filestor layer is paused.
     * Since there should be no data or synchronization dependencies on
     * _paused, use relaxed atomics.
     */
    bool isPaused() const { return _paused.load(std::memory_order_relaxed); }

    /**
     * Return whether msg has timed out based on waitTime and the message's
     * specified timeout.
     */
    static bool messageTimedOutInQueue(const api::StorageMessage& msg, vespalib::duration waitTime);

    /**
     * Creates and returns a reply with api::TIMEOUT return code for msg.
     * Swaps (invalidates) context from msg into reply.
     */
    static std::unique_ptr<api::StorageReply> makeQueueTimeoutReply(api::StorageMessage& msg);
    static bool messageMayBeAborted(const api::StorageMessage& msg);

    // Implements framework::MetricUpdateHook
    void updateMetrics(const MetricLockGuard &) override;

    document::Bucket
    remapMessage(api::StorageMessage& msg, const document::Bucket &source, Operation op,
                 std::vector<RemapInfo*>& targets, api::ReturnCode& returnCode);

    void remapQueueNoLock(const RemapInfo& source, std::vector<RemapInfo*>& targets, Operation op);

    /**
     * Waits until the queue has no pending operations (i.e. no locks are
     * being held.
     */
    void waitUntilNoLocks();
    /**
     * No assumption on memory ordering around disk state reads should
     * be made by callers.
     */
    DiskState getState() const noexcept {
        return _state.load(std::memory_order_relaxed);
    }
    /**
     * No assumption on memory ordering around disk state writes should
     * be made by callers.
     */
    void setState(DiskState s) noexcept {
        _state.store(s, std::memory_order_relaxed);
    }
    bool isClosed() const noexcept { return getState() == DiskState::CLOSED; }
    void dumpActiveHtml(std::ostream & os) const;
    void dumpQueueHtml(std::ostream & os) const;
    void flush();
    static uint64_t dispersed_bucket_bits(const document::Bucket& bucket) noexcept;

    // We make a fairly reasonable assumption that there will be less than 64k stripes.
    uint16_t stripe_index(const document::Bucket& bucket) const noexcept {
        return static_cast<uint16_t>(dispersed_bucket_bits(bucket) % _stripes.size());
    }
    Stripe & stripe(const document::Bucket & bucket) {
        return _stripes[stripe_index(bucket)];
    }
    FileStorHandler::LockedMessage getNextMessage(uint32_t stripeId, vespalib::duration timeout) {
        return _stripes[stripeId].getNextMessage(timeout);
    }

};

} // storage
