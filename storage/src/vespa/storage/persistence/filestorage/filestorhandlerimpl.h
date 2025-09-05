// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include "active_operations_stats.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/metrics/metrictimer.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>
#include <atomic>
#include <optional>

namespace storage {

class FileStorDiskMetrics;
class FileStorStripeMetrics;
class StorBucketDatabase;
class AbortBucketOperationsCommand;

class FileStorHandlerImpl final
    : private framework::MetricUpdateHook,
      private ResumeGuard::Callback,
      public FileStorHandler
{
public:
    struct MessageEntry {
        std::shared_ptr<api::StorageMessage> _command;
        metrics::MetricTimer _timer;
        document::Bucket _bucket;
        uint8_t _priority;

        MessageEntry(const std::shared_ptr<api::StorageMessage>& cmd,
                     const document::Bucket& bucket,
                     vespalib::steady_time scheduled_at_time);
        MessageEntry(MessageEntry&&) noexcept;
        MessageEntry(const MessageEntry&) noexcept;
        MessageEntry& operator=(const MessageEntry&) = delete;
        ~MessageEntry();

        bool operator<(const MessageEntry& entry) const noexcept {
            return (_priority < entry._priority);
        }
    };

    struct PriorityQueue {
        using EntryMap = std::unordered_map<uint64_t, MessageEntry>;
        using MapEntry = EntryMap::value_type;
        using EntryPtr = MapEntry *;
        using EntryCmp = bool(*)(const MessageEntry&, const MessageEntry&);
        template<EntryCmp cmp> struct OrderCmp {
            using is_transparent = std::true_type;
            bool operator() (EntryPtr a, EntryPtr b) const noexcept {
                const auto& [keyA, entryA] = *a;
                const auto& [keyB, entryB] = *b;
                if (cmp(entryA, entryB)) return true;
                if (cmp(entryB, entryA)) return false;
                return keyA < keyB;
            }
            template<typename T>
            bool operator() (EntryPtr a, const T& b) const noexcept { return b.convert(a) < b.convert(); }
            template<typename T>
            bool operator() (const T& a, EntryPtr b) const noexcept { return a.convert() < a.convert(b); }
        };
        static bool compareByPriority(const MessageEntry& a, const MessageEntry&b) {
            return a._priority < b._priority;
        }
        static bool compareByBucket(const MessageEntry& a, const MessageEntry&b) {
            return a._bucket < b._bucket;
        }
        using ByPriCmp = OrderCmp<compareByPriority>;
        using ByBucketCmp = OrderCmp<compareByBucket>;

        using ByPriSet = std::set<EntryPtr, ByPriCmp>;
        using ByBucketSet = std::set<EntryPtr, ByBucketCmp>;

        size_t size() const noexcept { return _main_map.size(); }
        bool empty() const noexcept { return _main_map.empty(); }
        void emplace_back(MessageEntry entry) {
            uint64_t seq_id = _next_sequence_id++;
            auto [iter, added] = _main_map.try_emplace(seq_id, std::move(entry));
            assert(added);
            MapEntry& me = *iter;
            _sequence_ids_by_priority.insert(&me);
            _sequence_ids_by_bucket.insert(&me);
        }

        void remove(uint64_t sequence_id) {
            auto iter = _main_map.find(sequence_id);
            assert(iter != _main_map.end());
            MapEntry& me = *iter;
            _sequence_ids_by_bucket.erase(&me);
            _sequence_ids_by_priority.erase(&me);
            _main_map.erase(iter);
        }

        template<typename I, typename V> struct ordered_iterator {
            using difference_type = I::difference_type;
            using value_type = V::second_type;
            using pointer = value_type *;
            using reference = value_type &;
            using iterator_category = std::input_iterator_tag;

            reference operator* () const noexcept { return deref()->second; };
            pointer operator-> () const noexcept { return &deref()->second; };
            void operator++() { ++_place; }
            bool operator==(const ordered_iterator& other) const noexcept {
                return _place == other._place;
            }
            bool operator!=(const ordered_iterator& other) const noexcept {
                return _place != other._place;
            }
            ordered_iterator(I p) : _place(p) {}
            ordered_iterator(const ordered_iterator&) = default;
            ordered_iterator& operator= (const ordered_iterator& other) = default;
            auto deref() const noexcept { return *_place; }
        private:
            I _place;
        };
        struct ConstPriorityIdxView {
            using const_iterator = ordered_iterator<ByPriSet::const_iterator, const MapEntry>;
            const PriorityQueue& _q;
            const_iterator begin() const noexcept { return _q._sequence_ids_by_priority.begin(); }
            const_iterator end() const noexcept { return _q._sequence_ids_by_priority.end(); }
            ConstPriorityIdxView(PriorityQueue& q) : _q(q) {}
        };
        struct PriorityIdxView {
            using iterator = ordered_iterator<ByPriSet::iterator, MapEntry>;
            PriorityQueue& _q;
            iterator begin() const noexcept { return _q._sequence_ids_by_priority.begin(); }
            iterator end() const noexcept { return _q._sequence_ids_by_priority.end(); }
            PriorityIdxView(PriorityQueue& q) : _q(q) {}
            iterator erase(iterator it) {
                EntryPtr p = it.deref();
                ++it;
                _q.remove(p->first);
                return it;
            }
        };
        struct BucketIdxView {
            using iterator = ordered_iterator<ByBucketSet::const_iterator, MapEntry>;
            PriorityQueue& _q;
            iterator begin() const noexcept { return _q._sequence_ids_by_bucket.begin(); }
            iterator end() const noexcept { return _q._sequence_ids_by_bucket.end(); }
            BucketIdxView(PriorityQueue& q) : _q(q) {}
            iterator erase(iterator it) {
                EntryPtr p = it.deref();
                ++it;
                _q.remove(p->first);
                return it;
            }
            void erase(iterator it, iterator to) {
                while (it != to) {
                    it = erase(it);
                }
            }
            struct BucketCompare {
                const document::Bucket& bucket;
                const document::Bucket& convert() const noexcept { return bucket; }
                const document::Bucket& convert(EntryPtr p) const noexcept { return p->second._bucket; }
            };
            std::pair<iterator, iterator> equal_range(const document::Bucket &bucket) {
                BucketCompare cmp(bucket);
                auto inner_range = _q._sequence_ids_by_bucket.equal_range(cmp);
                return std::make_pair(iterator(inner_range.first),
                                      iterator(inner_range.second));
            }
        };

        PriorityQueue()
          : _main_map(),
            _sequence_ids_by_priority(),
            _sequence_ids_by_bucket()
        {}
        ~PriorityQueue();

    private:
        uint64_t _next_sequence_id = 1;
        EntryMap _main_map;
        ByPriSet _sequence_ids_by_priority;
        ByBucketSet _sequence_ids_by_bucket;
    };

    using ConstPriorityIdxView = PriorityQueue::ConstPriorityIdxView;
    using PriorityIdxView = PriorityQueue::PriorityIdxView;
    using BucketIdxView = PriorityQueue::BucketIdxView;
    using Clock = std::chrono::steady_clock;
    using monitor_guard = std::unique_lock<std::mutex>;
    using atomic_size_t = vespalib::datastore::AtomicValueWrapper<size_t>;

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

        class SafeActiveOperationsStats {
        private:
            std::unique_ptr<std::mutex> _lock;
            ActiveOperationsStats _stats;
            struct ctor_tag {};
        public:
            class Guard {
            private:
                std::lock_guard<std::mutex> _guard;
                ActiveOperationsStats &_stats;
            public:
                Guard(Guard &&) = delete;
                Guard(std::mutex &lock, ActiveOperationsStats &stats_in, ctor_tag) : _guard(lock), _stats(stats_in) {}
                ActiveOperationsStats &stats() { return _stats; }
            };
            SafeActiveOperationsStats() : _lock(std::make_unique<std::mutex>()), _stats() {}
            [[nodiscard]] Guard guard() { return Guard(*_lock, _stats, ctor_tag()); }
        };

        Stripe(const FileStorHandlerImpl & owner, MessageSender & messageSender);
        Stripe(Stripe &&) noexcept;
        Stripe(const Stripe &) = delete;
        Stripe & operator =(const Stripe &) = delete;
        ~Stripe();
        void flush();
        [[nodiscard]] bool schedule(MessageEntry messageEntry);
        [[nodiscard]] FileStorHandler::LockedMessage schedule_and_get_next_async_message(MessageEntry entry);
        void waitUntilNoLocks() const;
        void abort(std::vector<std::shared_ptr<api::StorageReply>> & aborted, const AbortBucketOperationsCommand& cmd);
        void waitInactive(const AbortBucketOperationsCommand& cmd) const;

        void broadcast() {
            _cond->notify_all();
        }
        size_t get_cached_queue_size() const { return _cached_queue_size.load_relaxed(); }
        void unsafe_update_cached_queue_size() {
            _cached_queue_size.store_relaxed(_queue->size());
        }

        void release(const document::Bucket & bucket, api::LockingRequirements reqOfReleasedLock,
                     api::StorageMessage::Id lockMsgId, bool was_active_maintenance);
        void decrease_active_sync_maintenance_counter() noexcept;

        // Subsumes isLocked
        bool operationIsInhibited(const monitor_guard &, const document::Bucket&,
                                  const api::StorageMessage&) const noexcept;
        bool isLocked(const monitor_guard &, const document::Bucket&,
                      api::LockingRequirements lockReq) const noexcept;

        void lock(const monitor_guard &, const document::Bucket & bucket,
                  api::LockingRequirements lockReq, bool count_as_active_maintenance,
                  const LockEntry & lockEntry);

        [[nodiscard]] std::shared_ptr<FileStorHandler::BucketLockInterface> lock(const document::Bucket & bucket, api::LockingRequirements lockReq);
        void failOperations(const document::Bucket & bucket, const api::ReturnCode & code);
        [[nodiscard]] FileStorHandler::LockedMessage getNextMessage(vespalib::steady_time deadline);
        [[nodiscard]] FileStorHandler::LockedMessageBatch next_message_batch(vespalib::steady_time now, vespalib::steady_time deadline);
        void dumpQueue(std::ostream & os) const;
        void dumpActiveHtml(std::ostream & os) const;
        void dumpQueueHtml(std::ostream & os) const;
        [[nodiscard]] std::mutex & exposeLock() { return *_lock; }
        void queue_emplace(MessageEntry entry) { _queue->emplace_back(std::move(entry)); }
        [[nodiscard]] BucketIdxView exposeBucketIdxView() { return BucketIdxView(*_queue); }
        [[nodiscard]] PriorityIdxView exposePriorityIdxView() { return PriorityIdxView(*_queue); }
        [[nodiscard]] ConstPriorityIdxView exposePriorityIdxView() const noexcept { return ConstPriorityIdxView(*_queue); }
        void setMetrics(FileStorStripeMetrics * metrics) { _metrics = metrics; }
        [[nodiscard]] ActiveOperationsStats get_active_operations_stats(bool reset_min_max) const;
    private:
        void update_cached_queue_size(const std::lock_guard<std::mutex> &) {
            _cached_queue_size.store_relaxed(_queue->size());
        }
        void update_cached_queue_size(const std::unique_lock<std::mutex> &) {
            _cached_queue_size.store_relaxed(_queue->size());
        }
        [[nodiscard]] bool hasActive(monitor_guard & monitor, const AbortBucketOperationsCommand& cmd) const;
        [[nodiscard]] FileStorHandler::LockedMessage get_next_async_message(monitor_guard& guard);
        [[nodiscard]] bool operation_type_should_be_throttled(api::MessageType::Id type_id) const noexcept;

        [[nodiscard]] FileStorHandler::LockedMessage next_message_impl(monitor_guard& held_lock,
                                                                       vespalib::steady_time deadline);
        void fill_feed_op_batch(monitor_guard& held_lock, LockedMessageBatch& batch,
                                uint32_t max_batch_size, vespalib::steady_time now);

        // Precondition: the bucket used by `iter`s operation is not locked in a way that conflicts
        // with its locking requirements.
        [[nodiscard]] FileStorHandler::LockedMessage getMessage(monitor_guard & guard, PriorityIdxView & idx,
                                                                PriorityIdxView::iterator iter,
                                                                ThrottleToken throttle_token);
        using LockedBuckets = vespalib::hash_map<document::Bucket, MultiLockEntry, document::Bucket::hash>;
        const FileStorHandlerImpl      &_owner;
        MessageSender                  &_messageSender;
        FileStorStripeMetrics          *_metrics;
        std::unique_ptr<std::mutex>                _lock;
        std::unique_ptr<std::condition_variable>   _cond;
        std::unique_ptr<PriorityQueue>  _queue;
        atomic_size_t                   _cached_queue_size;
        LockedBuckets                   _lockedBuckets;
        uint32_t                        _active_maintenance_ops;
        mutable SafeActiveOperationsStats _active_operations_stats;
    };

    class BucketLock final : public BucketLockInterface {
    public:
        // TODO refactor, too many params
        BucketLock(const monitor_guard& guard,
                   Stripe& disk,
                   const document::Bucket& bucket,
                   uint8_t priority, api::MessageType::Id msgType, api::StorageMessage::Id,
                   api::LockingRequirements lockReq);
        ~BucketLock() override;

        const document::Bucket &getBucket() const override { return _bucket; }
        api::LockingRequirements lockingRequirements() const noexcept override { return _lockReq; }
        void signal_operation_sync_phase_done() noexcept override;
        bool wants_sync_phase_done_notification() const noexcept override {
            return _counts_towards_maintenance_limit;
        }

    private:
        Stripe                 & _stripe;
        const document::Bucket   _bucket;
        api::StorageMessage::Id  _uniqueMsgId;
        api::LockingRequirements _lockReq;
        bool                     _counts_towards_maintenance_limit;
    };


    FileStorHandlerImpl(MessageSender& sender, FileStorMetrics& metrics,
                        ServiceLayerComponentRegister& compReg);
    FileStorHandlerImpl(uint32_t numThreads, uint32_t numStripes, MessageSender&, FileStorMetrics&,
                        ServiceLayerComponentRegister&,
                        const vespalib::SharedOperationThrottler::DynamicThrottleParams& op_dyn_throttle_params,
                        const vespalib::SharedOperationThrottler::DynamicThrottleParams& maintenance_dyn_throttle_params);

    ~FileStorHandlerImpl() override;

    void flush(bool killPendingMerges) override;
    void setDiskState(DiskState state) override;
    DiskState getDiskState() const override;
    void close() override;
    bool schedule(const std::shared_ptr<api::StorageMessage>&) override;
    ScheduleAsyncResult schedule_and_get_next_async_message(const std::shared_ptr<api::StorageMessage>& msg) override;

    LockedMessage getNextMessage(uint32_t stripeId, vespalib::steady_time deadline) override;
    LockedMessageBatch next_message_batch(uint32_t stripe, vespalib::steady_time now, vespalib::steady_time deadline) override;

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
    std::shared_ptr<MergeStatus> editMergeStatus(const document::Bucket&) override;
    bool isMerging(const document::Bucket&) const override;
    void clearMergeStatus(const document::Bucket& bucket) override;
    void clearMergeStatus(const document::Bucket& bucket, const api::ReturnCode& code) override;

    void clearMergeStatus(const document::Bucket&, const api::ReturnCode*);

    std::string dumpQueue() const override;
    ResumeGuard pause() override;
    void abortQueuedOperations(const AbortBucketOperationsCommand& cmd) override;

    vespalib::SharedOperationThrottler& operation_throttler() const noexcept override {
        // It would be reasonable to assume that this could be a relaxed load since the set
        // of possible throttlers is static and all _persistence_ thread creation is sequenced
        // after throttler creation. But since the throttler may be invoked by RPC threads
        // created in another context, use acquire semantics to ensure transitive visibility.
        // TODO remove need for atomics once the throttler testing dust settles
        return *_active_throttler.load(std::memory_order_acquire);
    }

    vespalib::SharedOperationThrottler& maintenance_throttler() const noexcept override {
        // See rationale in operation_throttler() for memory ordering.
        return *_active_throttler.load(std::memory_order_acquire);
    }

    void reconfigure_dynamic_operation_throttler(const vespalib::SharedOperationThrottler::DynamicThrottleParams& params) override;
    void reconfigure_dynamic_maintenance_throttler(const vespalib::SharedOperationThrottler::DynamicThrottleParams& params) override;

    void use_dynamic_operation_throttling(bool use_dynamic) noexcept override;
    void use_dynamic_maintenance_throttling(bool use_dynamic) noexcept override;

    void set_throttle_apply_bucket_diff_ops(bool throttle_apply_bucket_diff) noexcept override {
        // Relaxed is fine, worst case from temporarily observing a stale value is that
        // an ApplyBucketDiff message is (or isn't) throttled at a high level.
        _throttle_apply_bucket_diff_ops.store(throttle_apply_bucket_diff, std::memory_order_relaxed);
    }

    void set_max_feed_op_batch_size(uint32_t max_batch) noexcept override {
        _max_feed_op_batch_size.store(max_batch, std::memory_order_relaxed);
    }
    [[nodiscard]] uint32_t max_feed_op_batch_size() const noexcept {
        return _max_feed_op_batch_size.load(std::memory_order_relaxed);
    }

    // Implements ResumeGuard::Callback
    void resume() override;

    // Use only for testing
    framework::MetricUpdateHook& get_metric_update_hook_for_testing() { return *this; }

private:
    ServiceLayerComponent   _component;
    std::atomic<DiskState>  _state;
    FileStorMetrics       * _metrics;
    std::unique_ptr<vespalib::SharedOperationThrottler> _dynamic_operation_throttler;
    std::unique_ptr<vespalib::SharedOperationThrottler> _unlimited_operation_throttler;
    std::atomic<vespalib::SharedOperationThrottler*>    _active_throttler;
    std::unique_ptr<vespalib::SharedOperationThrottler> _dynamic_maintenance_throttler;
    std::unique_ptr<vespalib::SharedOperationThrottler> _unlimited_maintenance_throttler;
    std::atomic<vespalib::SharedOperationThrottler*>    _active_maintenance_throttler;
    std::vector<Stripe>     _stripes;
    MessageSender&          _messageSender;
    const document::BucketIdFactory& _bucketIdFactory;
    mutable std::mutex    _mergeStatesLock;
    std::map<document::Bucket, std::shared_ptr<MergeStatus>> _mergeStates;
    const uint32_t        _max_active_maintenance_ops_per_stripe; // Read concurrently by stripes.
    mutable std::mutex              _pauseMonitor;
    mutable std::condition_variable _pauseCond;
    std::atomic<bool>               _paused;
    std::atomic<bool>               _throttle_apply_bucket_diff_ops;
    std::optional<ActiveOperationsStats> _last_active_operations_stats;
    std::atomic<uint32_t>           _max_feed_op_batch_size;

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

    [[nodiscard]] bool throttle_apply_bucket_diff_ops() const noexcept {
        return _throttle_apply_bucket_diff_ops.load(std::memory_order_relaxed);
    }

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

    void update_active_operations_metrics();

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

    ActiveOperationsStats get_active_operations_stats(bool reset_min_max) const override;
};

} // storage
