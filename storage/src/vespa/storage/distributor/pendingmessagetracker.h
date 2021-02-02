// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "nodeinfo.h"
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/component/componentregister.h>
#include <vespa/storageframework/generic/component/component.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>
#include <boost/multi_index/composite_key.hpp>
#include <set>
#include <unordered_map>
#include <chrono>
#include <mutex>

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

    /**
     * Time point represented as the millisecond interval from the framework
     * clock's epoch to a given point in time. Note that it'd be more
     * semantically correct to use std::chrono::time_point, but it is bound
     * to specific chrono clock types, their epochs and duration resolution.
     */
    using TimePoint = std::chrono::milliseconds;

    explicit PendingMessageTracker(framework::ComponentRegister&);
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
    void checkPendingMessages(uint16_t node, const document::Bucket &bucket, Checker& checker) const;

    /**
     * Goes through each pending message (across all nodes) for the given bucket
     * and invokes the given checker with the node, message type and priority.
     * Breaks when the checker returns false.
     */
    void checkPendingMessages(const document::Bucket &bucket, Checker& checker) const;

    /**
     * Utility function for checking if there's a message of type
     * messageType pending to bucket bid on the given node.
     */
    bool hasPendingMessage(uint16_t node, const document::Bucket &bucket, uint32_t messageType) const;

    /**
     * Returns a vector containing the number of pending messages to each storage node.
     * The vector might be smaller than a given node index. In that case, that storage
     * node has never had any pending messages.
     */
    const NodeInfo& getNodeInfo() const { return _nodeInfo; }
    NodeInfo& getNodeInfo() { return _nodeInfo; }

    /**
     * Clears all pending messages for the given node, and returns
     * the messages erased.
     */
    std::vector<uint64_t> clearMessagesForNode(uint16_t node);

    void setNodeBusyDuration(std::chrono::seconds secs) noexcept {
        _nodeBusyDuration = secs;
    }

    void run_once_no_pending_for_bucket(const document::Bucket& bucket, std::unique_ptr<DeferredTask> task);
    void abort_deferred_tasks();
private:
    struct MessageEntry {
        TimePoint        timeStamp;
        uint32_t         msgType;
        uint32_t         priority;
        uint64_t         msgId;
        document::Bucket bucket;
        uint16_t         nodeIdx;

        MessageEntry(TimePoint timeStamp, uint32_t msgType, uint32_t priority,
                     uint64_t msgId, document::Bucket bucket, uint16_t nodeIdx) noexcept;
        vespalib::string toHtml() const;
    };

    struct MessageIdKey : boost::multi_index::member<MessageEntry, uint64_t, &MessageEntry::msgId> {};

    /**
     * Each entry has a separate composite keyed index on node+bucket id+type.
     * This makes it efficient to find all messages for a node, for a bucket
     * on that node and specific message types to an exact bucket on the node.
     */
    struct CompositeNodeBucketKey
        : boost::multi_index::composite_key<
              MessageEntry,
              boost::multi_index::member<MessageEntry, uint16_t, &MessageEntry::nodeIdx>,
              boost::multi_index::member<MessageEntry, document::Bucket, &MessageEntry::bucket>,
              boost::multi_index::member<MessageEntry, uint32_t, &MessageEntry::msgType>
          >
    {
    };

    struct CompositeBucketMsgNodeKey
        : boost::multi_index::composite_key<
              MessageEntry,
              boost::multi_index::member<MessageEntry, document::Bucket, &MessageEntry::bucket>,
              boost::multi_index::member<MessageEntry, uint32_t, &MessageEntry::msgType>,
              boost::multi_index::member<MessageEntry, uint16_t, &MessageEntry::nodeIdx>
          >
    {
    };

    using Messages = boost::multi_index::multi_index_container <
        MessageEntry,
        boost::multi_index::indexed_by<
            boost::multi_index::ordered_unique<MessageIdKey>,
            boost::multi_index::ordered_non_unique<CompositeNodeBucketKey>,
            boost::multi_index::ordered_non_unique<CompositeBucketMsgNodeKey>
        >
    >;

    using MessagesByMsgId         = Messages::nth_index<0>::type;
    using MessagesByNodeAndBucket = Messages::nth_index<1>::type;
    using MessagesByBucketAndType = Messages::nth_index<2>::type;
    using DeferredBucketTaskMap   = std::unordered_multimap<
            document::Bucket,
            std::unique_ptr<DeferredTask>,
            document::Bucket::hash
        >;

    Messages              _messages;
    framework::Component  _component;
    NodeInfo              _nodeInfo;
    std::chrono::seconds  _nodeBusyDuration;
    DeferredBucketTaskMap _deferred_read_tasks;

    // Since distributor is currently single-threaded, this will only
    // contend when status page is being accessed. It is, however, required
    // to be present for that exact purpose.
    mutable std::mutex _lock;

    void getStatusStartPage(std::ostream& out) const;
    void getStatusPerNode(std::ostream& out) const;
    void getStatusPerBucket(std::ostream& out) const;
    TimePoint currentTime() const;

    [[nodiscard]] bool bucket_has_no_pending_write_ops(const document::Bucket& bucket) const noexcept;
    std::vector<std::unique_ptr<DeferredTask>> get_deferred_ops_if_bucket_writes_drained(const document::Bucket&);
};

}
