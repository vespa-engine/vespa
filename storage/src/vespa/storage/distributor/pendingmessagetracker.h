// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "nodeinfo.h"
#include "latency_statistics_provider.h"
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/component/componentregister.h>
#include <vespa/storageframework/generic/component/component.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/sync.h>
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>
#include <boost/multi_index/composite_key.hpp>

#include <set>
#include <unordered_map>

namespace storage {
namespace distributor {

class PendingMessageTracker : public framework::HtmlStatusReporter
{
    class ForwardingLatencyStatisticsProvider
        : public LatencyStatisticsProvider
    {
        PendingMessageTracker& _messageTracker;
    public:
        ForwardingLatencyStatisticsProvider(
                PendingMessageTracker& messageTracker)
            : _messageTracker(messageTracker)
        {
        }

        NodeStatsSnapshot doGetLatencyStatistics() const override;
    };

public:
    class Checker {
    public:
        virtual ~Checker() {}

        virtual bool check(uint32_t messageType,
                           uint16_t node,
                           uint8_t priority) = 0;
    };

    /**
     * Time point represented as the millisecond interval from the framework
     * clock's epoch to a given point in time. Note that it'd be more
     * semantically correct to use std::chrono::time_point, but it is bound
     * to specific chrono clock types, their epochs and duration resolution.
     */
    using TimePoint = std::chrono::milliseconds;

    PendingMessageTracker(framework::ComponentRegister&);
    ~PendingMessageTracker();

    void insert(const std::shared_ptr<api::StorageMessage>&);
    document::BucketId reply(const api::StorageReply& reply);
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const;

    /**
     * Goes through each pending message for the given node+bucket pair,
     * passing it to the given type checker.
     * Breaks when the checker returns false.
     */
    void checkPendingMessages(uint16_t node,
                              const document::BucketId& bid,
                              Checker& checker) const;

    /**
     * Goes through each pending message (across all nodes) for the given bucket
     * and invokes the given checker with the node, message type and priority.
     * Breaks when the checker returns false.
     */
    void checkPendingMessages(const document::BucketId& bid,
                              Checker& checker) const;

    /**
     * Utility function for checking if there's a message of type
     * messageType pending to bucket bid on the given node.
     */
    bool hasPendingMessage(uint16_t node,
                           const document::BucketId& bid,
                           uint32_t messageType) const;

    /**
     * Returns a vector containing the number of pending messages to each storage node.
     * The vector might be smaller than a given node index. In that case, that storage
     * node has never had any pending messages.
     */
    const NodeInfo& getNodeInfo() const { return _nodeInfo; }
    NodeInfo& getNodeInfo() { return _nodeInfo; }

    /**
     * Get the statistics for all completed operations towards a specific
     * storage node. "Completed" here means both successful and failed
     * operations. Statistics are monotonically increasing within the scope of
     * the process' lifetime and are never reset. This models how the Linux
     * kernel reports its internal stats and means the caller must maintan
     * value snapshots to extract meaningful time series information.
     *
     * If stats are requested for a node that has not had any operations
     * complete towards it, the returned stats will be all zero.
     *
     * Method is thread safe and data race free.
     *
     * It is assumed that NodeStats is sufficiently small that returning it
     * by value does not incur a measurable performance impact. This also
     * prevents any data race issues in case returned stats are e.g.
     * concurrently read by another thread such the metric snapshotting thread.
     */
    NodeStats getNodeStats(uint16_t node) const;

    /**
     * Clears all pending messages for the given node, and returns
     * the messages erased.
     */
    std::vector<uint64_t> clearMessagesForNode(uint16_t node);

    /**
     * Must not be called when _lock is already held or there will be a
     * deadlock.
     */
    NodeStatsSnapshot getLatencyStatistics() const;

    LatencyStatisticsProvider& getLatencyStatisticsProvider() {
        return _statisticsForwarder;
    }

private:
    struct MessageEntry {
        TimePoint timeStamp;
        uint32_t msgType;
        uint32_t priority;
        uint64_t msgId;
        document::BucketId bucketId;
        uint16_t nodeIdx;
        vespalib::string msgText;

        MessageEntry(TimePoint timeStamp,
                     uint32_t msgType,
                     uint32_t priority,
                     uint64_t msgId,
                     document::BucketId bucketId,
                     uint16_t nodeIdx,
                     const vespalib::string & msgText);
    };

    struct MessageIdKey
        : boost::multi_index::member<MessageEntry, uint64_t, &MessageEntry::msgId>
    {
    };

    /**
     * Each entry has a separate composite keyed index on node+bucket id+type.
     * This makes it efficient to find all messages for a node, for a bucket
     * on that node and specific message types to an exact bucket on the node.
     */
    struct CompositeNodeBucketKey
        : boost::multi_index::composite_key<
              MessageEntry,
              boost::multi_index::member<MessageEntry, uint16_t,
                                         &MessageEntry::nodeIdx>,
              boost::multi_index::member<MessageEntry, document::BucketId,
                                         &MessageEntry::bucketId>,
              boost::multi_index::member<MessageEntry, uint32_t,
                                         &MessageEntry::msgType>
          >
    {
    };

    struct CompositeBucketMsgNodeKey
        : boost::multi_index::composite_key<
              MessageEntry,
              boost::multi_index::member<MessageEntry, document::BucketId,
                                         &MessageEntry::bucketId>,
              boost::multi_index::member<MessageEntry, uint32_t,
                                         &MessageEntry::msgType>,
              boost::multi_index::member<MessageEntry, uint16_t,
                                         &MessageEntry::nodeIdx>
          >
    {
    };

    typedef boost::multi_index::multi_index_container <
        MessageEntry,
        boost::multi_index::indexed_by<
            boost::multi_index::ordered_unique<MessageIdKey>,
            boost::multi_index::ordered_non_unique<CompositeNodeBucketKey>,
            boost::multi_index::ordered_non_unique<CompositeBucketMsgNodeKey>
        >
    > Messages;

    typedef Messages::nth_index<0>::type MessagesByMsgId;
    typedef Messages::nth_index<1>::type MessagesByNodeAndBucket;
    typedef Messages::nth_index<2>::type MessagesByBucketAndType;

    Messages _messages;
    framework::Component _component;
    std::unordered_map<uint16_t, NodeStats> _nodeIndexToStats;
    NodeInfo _nodeInfo;
    ForwardingLatencyStatisticsProvider _statisticsForwarder;

    // Since distributor is currently single-threaded, this will only
    // contend when status page is being accessed. It is, however, required
    // to be present for that exact purpose.
    vespalib::Lock _lock;

    /**
     * Increment latency and operation count stats for the node the message
     * was sent towards based on the registered send time and the current time.
     *
     * In the event that system time has moved backwards across sending a
     * command and reciving its reply, the latency will not be recorded but
     * the total number of messages will increase.
     *
     * _lock MUST be held upon invocation.
     */
    void updateNodeStatsOnReply(const MessageEntry& entry);


    /**
     * Modifies opStats in-place with added latency based on delta from send
     * time to current time and incremented operation count.
     */
    void updateOperationStats(OperationStats& opStats,
                              const MessageEntry& entry) const;


    void getStatusStartPage(std::ostream& out) const;
    void getStatusPerNode(std::ostream& out) const;
    void getStatusPerBucket(std::ostream& out) const;
    TimePoint currentTime() const;
};

}
}
