// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "content_node_message_stats_tracker.h"
#include "nodeinfo.h"
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageframework/generic/component/component.h>
#include <vespa/storageframework/generic/component/componentregister.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <cassert>
#include <chrono>
#include <functional>
#include <mutex>
#include <set>
#include <unordered_map>

namespace storage::distributor {

/**
 * Since the state a deferred task depends on may have changed between the
 * time a task was scheduled and when it's actually executed, this enum provides
 * a means of communicating if a task should be started as normal.
 */
enum class TaskRunState {
    OK,        // Task may be started as normal
    Aborted,   // Task should trigger an immediate abort behavior (distributor is shutting down)
    BucketLost // Task should trigger an immediate abort behavior (bucket no longer present on node)
};

/**
 * Represents an arbitrary task whose execution may be deferred until no
 * further pending operations are present.
 */
struct DeferredTask {
    virtual ~DeferredTask() = default;
    virtual void run(TaskRunState state) = 0;
};

template <typename Func>
class LambdaDeferredTask : public DeferredTask {
    Func _func;
public:
    explicit LambdaDeferredTask(Func&& f) : _func(std::move(f)) {}
    LambdaDeferredTask(const LambdaDeferredTask&) = delete;
    LambdaDeferredTask(LambdaDeferredTask&&) = delete;
    ~LambdaDeferredTask() override = default;

    void run(TaskRunState state) override {
        _func(state);
    }
};

template <typename Func>
std::unique_ptr<DeferredTask> make_deferred_task(Func&& f) {
    return std::make_unique<LambdaDeferredTask<std::decay_t<Func>>>(std::forward<Func>(f));
}

class PendingMessageTracker : public framework::HtmlStatusReporter {
public:
    class Checker {
    public:
        virtual ~Checker() = default;
        virtual bool check(uint32_t messageType, uint16_t node, uint8_t priority) = 0;
    };

    using TimePoint = vespalib::system_time;

    PendingMessageTracker(framework::ComponentRegister&, uint32_t stripe_index);
    ~PendingMessageTracker() override;

    void insert(const std::shared_ptr<api::StorageMessage>&);
    document::Bucket reply(const api::StorageReply& reply);
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const;

    /**
     * Goes through each pending message for the given node+bucket pair,
     * passing it to the given type checker.
     * Breaks when the checker returns false.
     */
    void checkPendingMessages(uint16_t node, const document::Bucket& bucket, Checker& checker) const;

    /**
     * Goes through each pending message (across all nodes) for the given bucket
     * and invokes the given checker with the node, message type and priority.
     * Breaks when the checker returns false.
     */
    void checkPendingMessages(const document::Bucket& bucket, Checker& checker) const;

    /**
     * Utility function for checking if there's a message of type
     * messageType pending to bucket bid on the given node.
     */
    bool hasPendingMessage(uint16_t node, const document::Bucket& bucket, uint32_t messageType) const;

    /**
     * Returns a vector containing the number of pending messages to each storage node.
     * The vector might be smaller than a given node index. In that case, that storage
     * node has never had any pending messages.
     */
    const NodeInfo& getNodeInfo() const noexcept { return _nodeInfo; }
    NodeInfo& getNodeInfo() noexcept { return _nodeInfo; }

    [[nodiscard]] ContentNodeMessageStatsTracker::NodeStats content_node_stats() const;

    /**
     * Clears all pending messages for the given node, and returns
     * the messages erased.
     */
    std::vector<uint64_t> clearMessagesForNode(uint16_t node);

    void setNodeBusyDuration(vespalib::duration duration) noexcept {
        _nodeBusyDuration = duration;
    }

    void run_once_no_pending_for_bucket(const document::Bucket& bucket, std::unique_ptr<DeferredTask> task);
    void abort_deferred_tasks();

    /**
     * For each distinct bucket with at least one pending message towards it:
     *
     * Iff `bucket_predicate(bucket) == true`, `msg_id_callback` is invoked once for _each_
     * message towards `bucket`, with the message ID as the argument.
     *
     * Note: `bucket_predicate` is only invoked once per distinct bucket.
     */
    void enumerate_matching_pending_bucket_ops(const std::function<bool(const document::Bucket&)>& bucket_predicate,
                                               const std::function<void(uint64_t)>& msg_id_callback) const;
private:
    // these are all the full and partial keys for the first extra index:
    using NodeBucketTypeIdKey =
            std::tuple<uint16_t, const document::Bucket &, uint32_t, uint64_t>;
    using NodeBucketTypeKey =
            std::tuple<uint16_t, const document::Bucket &, uint32_t>;
    using NodeBucketKey =
            std::tuple<uint16_t, const document::Bucket &>;
    using NodeKey =
            std::tuple<uint16_t>;
    // these are all the full and partial keys for the second extra index:
    using BucketTypeNodeIdKey =
            std::tuple<const document::Bucket &, uint32_t, uint16_t, uint64_t>;
    using BucketTypeNodeKey =
            std::tuple<const document::Bucket &, uint32_t, uint16_t>;
    using BucketTypeKey =
            std::tuple<const document::Bucket &, uint32_t>;
    using BucketKey =
            std::tuple<const document::Bucket &>;

    struct MessageEntry {
        TimePoint        timeStamp;
        uint32_t         msgType;
        uint32_t         priority;
        uint64_t         msgId;
        document::Bucket bucket;
        uint16_t         nodeIdx;

        MessageEntry(TimePoint timeStamp, uint32_t msgType, uint32_t priority,
                     uint64_t msgId, document::Bucket bucket, uint16_t nodeIdx) noexcept;
        [[nodiscard]] std::string toHtml() const;

        // make it easy to implement comparison operators:
        operator NodeBucketTypeIdKey() const {
            return NodeBucketTypeIdKey(nodeIdx, bucket, msgType, msgId);
        }
        operator NodeBucketTypeKey() const {
            return NodeBucketTypeKey(nodeIdx, bucket, msgType);
        }
        operator NodeBucketKey() const {
            return NodeBucketKey(nodeIdx, bucket);
        }
        operator NodeKey() const {
            return NodeKey(nodeIdx);
        }
        operator BucketTypeNodeIdKey() const {
            return BucketTypeNodeIdKey(bucket, msgType, nodeIdx, msgId);
        }
        operator BucketKey() const {
            return BucketKey(bucket);
        }
    };

    // comparator using just the 64-bit unique msgId:
    struct MessageIdKey {
        using is_transparent = std::true_type;
        bool operator() (const MessageEntry &a, const MessageEntry &b) const {
            return a.msgId < b.msgId;
        }
        bool operator() (const MessageEntry &a, uint64_t b) const {
            return a.msgId < b;
        }
        bool operator() (uint64_t a, const MessageEntry &b) const {
            return a < b.msgId;
        }
    };

    /**
     * Each entry has a separate composite keyed index on node+bucket id+type.
     * This makes it efficient to find all messages for a node, for a bucket
     * on that node and specific message types to an exact bucket on the node.
     */
    struct NodeBucketTypeIdComparator {
        using is_transparent = std::true_type;
        bool operator() (const MessageEntry *a, const MessageEntry *b) const {
            NodeBucketTypeIdKey ka(*a);
            NodeBucketTypeIdKey kb(*b);
            return ka < kb;
        }
        // allow compare both ways with partial Key tuples:
        template<typename T> bool operator() (const MessageEntry * a, const T& b) const { return T(*a) < b; }
        template<typename T> bool operator() (const T& a, const MessageEntry * b) const { return a < T(*b); }
    };

    // We also have an index keyed no bucket id+type+node
    // currently only used to gather all messages for a specific bucket,
    // so maybe it could be simplified
    struct BucketTypeNodeIdComparator {
        using is_transparent = std::true_type;
        bool operator() (const MessageEntry *a, const MessageEntry *b) const {
            BucketTypeNodeIdKey ka(*a);
            BucketTypeNodeIdKey kb(*b);
            return ka < kb;
        }
        // allow compare both ways with partial Key tuples:
        template<typename T> bool operator() (const MessageEntry * a, const T& b) const { return T(*a) < b; }
        template<typename T> bool operator() (const T& a, const MessageEntry * b) const { return a < T(*b); }
    };

    // wraps an iterator for std::set<MessageEntry *>, hiding the extra indirection
    template<typename I> struct wrap_set_iterator : public I {
        auto * operator-> () const { return I::operator*(); }
        auto & operator* () const { return *I::operator*(); }
    };

    // multi-index container:
    struct Messages {
        using MainSet = std::set<MessageEntry, MessageIdKey>;
        using ByNodeAndBucketSet = std::set<const MessageEntry *, NodeBucketTypeIdComparator>;
        using ByBucketAndTypeSet = std::set<const MessageEntry *, BucketTypeNodeIdComparator>;

        // generic emplace, keeping indexes in sync
        template<typename ...Args>
        void emplace(Args&&... args)
        {
            auto [iter, added] = byMessageIdSet.emplace(std::forward<Args>(args)...);
            assert(added);
            const MessageEntry &entry = *iter;
            byNodeAndBucketSet.insert(&entry);
            byBucketAndTypeSet.insert(&entry);
        }

        // remove by key, keeping indexes in sync
        void remove(const MessageEntry &key) {
            auto iter = byMessageIdSet.find(key);
            assert (iter != byMessageIdSet.end());
            const MessageEntry &entry = *iter;
            byNodeAndBucketSet.erase(&entry);
            byBucketAndTypeSet.erase(&entry);
            byMessageIdSet.erase(iter);
        }

        // Index wrapping the set using NodeBucketTypeIdComparator
        struct IndexByNodeAndBucket {
            using iterator = wrap_set_iterator<ByNodeAndBucketSet::const_iterator>;
            Messages& _m;
            auto begin() const { return iterator(_m.byNodeAndBucketSet.begin()); }
            auto end()   const { return iterator(_m.byNodeAndBucketSet.end()); }

            IndexByNodeAndBucket(Messages& m) : _m(m) {}
            template<typename Key>
            std::pair<iterator, iterator> equal_range(Key key) const {
                auto inner_range = _m.byNodeAndBucketSet.equal_range(key);
                return std::make_pair(iterator(inner_range.first),
                                      iterator(inner_range.second));
            }
            iterator erase(iterator it) {
                const MessageEntry& entry = *it;
                ++it;
                _m.remove(entry);
                return it;
            }
            void erase(iterator it, iterator to) {
                while (it != to) {
                    it = erase(it);
                }
            }
        };

        // Index wrapping the set using BucketTypeNodeIdComparator
        struct IndexByBucketAndType {
            using iterator = wrap_set_iterator<ByBucketAndTypeSet::const_iterator>;
            Messages& _m;
            auto begin() const { return iterator(_m.byBucketAndTypeSet.begin()); }
            auto end()   const { return iterator(_m.byBucketAndTypeSet.end()); }
            std::pair<iterator, iterator> equal_range(BucketKey key) const {
                auto inner_range = _m.byBucketAndTypeSet.equal_range(key);
                return std::make_pair(iterator(inner_range.first),
                                      iterator(inner_range.second));
            }
            IndexByBucketAndType(Messages& m) : _m(m) {}
        };

        // the actual contents:
        MainSet byMessageIdSet;
        // sets of pointers:
        ByNodeAndBucketSet   byNodeAndBucketSet;
        ByBucketAndTypeSet   byBucketAndTypeSet;
        // index wrappers:
        IndexByNodeAndBucket byNodeAndBucketIdx;
        IndexByBucketAndType byBucketAndTypeIdx;

        Messages()
          : byMessageIdSet(),
            byNodeAndBucketSet(),
            byBucketAndTypeSet(),
            byNodeAndBucketIdx(*this),
            byBucketAndTypeIdx(*this)
        {}

        ~Messages();
    };

    using DeferredBucketTaskMap = std::unordered_multimap<
            document::Bucket,
            std::unique_ptr<DeferredTask>,
            document::Bucket::hash
        >;

    Messages                       _messages;
    framework::Component           _component;
    NodeInfo                       _nodeInfo;
    vespalib::duration             _nodeBusyDuration;
    DeferredBucketTaskMap          _deferred_read_tasks;
    ContentNodeMessageStatsTracker _node_message_stats_tracker;
    mutable std::atomic<bool>      _trackTime;

    // Protects sampling of content node statistics and status page rendering, as this can happen
    // from arbitrary other threads than the owning stripe's worker thread.
    mutable std::mutex _lock;

    void getStatusStartPage(std::ostream& out) const;
    void getStatusPerNode(std::ostream& out) const;
    void getStatusPerBucket(std::ostream& out) const;
    TimePoint currentTime() const;

    [[nodiscard]] bool bucket_has_no_pending_write_ops(const document::Bucket& bucket) const noexcept;
    [[nodiscard]] std::vector<std::unique_ptr<DeferredTask>> get_deferred_ops_if_bucket_writes_drained(const document::Bucket&);
    void get_and_erase_deferred_tasks_for_bucket(const document::Bucket&, std::vector<std::unique_ptr<DeferredTask>>&);
};

}
