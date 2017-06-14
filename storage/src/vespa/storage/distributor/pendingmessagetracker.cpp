// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "pendingmessagetracker.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <map>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".pendingmessages");

namespace storage {

namespace distributor {

PendingMessageTracker::PendingMessageTracker(framework::ComponentRegister& cr)
    : framework::HtmlStatusReporter("pendingmessages",
                                    "Pending messages to storage nodes"),
      _component(cr, "pendingmessagetracker"),
      _nodeIndexToStats(),
      _nodeInfo(_component.getClock()),
      _statisticsForwarder(*this),
      _lock()
{
    _component.registerStatusPage(*this);
}

PendingMessageTracker::~PendingMessageTracker()
{
}

PendingMessageTracker::MessageEntry::MessageEntry(
        TimePoint timeStamp_,
        uint32_t msgType_,
        uint32_t priority_,
        uint64_t msgId_,
        document::BucketId bucketId_,
        uint16_t nodeIdx_,
        const vespalib::string & msgText_)
    : timeStamp(timeStamp_),
      msgType(msgType_),
      priority(priority_),
      msgId(msgId_),
      bucketId(bucketId_),
      nodeIdx(nodeIdx_),
      msgText(msgText_)
{
}

PendingMessageTracker::TimePoint
PendingMessageTracker::currentTime() const
{
    return TimePoint(_component.getClock().getTimeInMillis().getTime());
}

namespace {

template <typename Pair>
struct PairAsRange
{
    Pair _pair;
    explicit PairAsRange(Pair pair) : _pair(std::move(pair)) {}

    auto begin() -> decltype(_pair.first) { return _pair.first; }
    auto end() -> decltype(_pair.second) { return _pair.second; }
    auto begin() const -> decltype(_pair.first) { return _pair.first; }
    auto end() const -> decltype(_pair.second) { return _pair.second; }
};

template <typename Pair>
PairAsRange<Pair>
pairAsRange(Pair pair)
{
    return PairAsRange<Pair>(std::move(pair));
}

}

std::vector<uint64_t>
PendingMessageTracker::clearMessagesForNode(uint16_t node)
{
    vespalib::LockGuard guard(_lock);
    MessagesByNodeAndBucket& idx(boost::multi_index::get<1>(_messages));
    auto range = pairAsRange(idx.equal_range(boost::make_tuple(node)));

    std::vector<uint64_t> erasedIds;
    for (auto& entry : range) {
        erasedIds.push_back(entry.msgId);
    }
    idx.erase(std::begin(range), std::end(range));

    _nodeInfo.clearPending(node);
    return erasedIds;
}

void
PendingMessageTracker::insert(
        const std::shared_ptr<api::StorageMessage>& msg)
{
    vespalib::LockGuard guard(_lock);
    if (msg->getAddress()) {
        _messages.insert(
                MessageEntry(currentTime(),
                             msg->getType().getId(),
                             msg->getPriority(),
                             msg->getMsgId(),
                             msg->getBucketId(),
                             msg->getAddress()->getIndex(),
                             msg->getSummary()));

        _nodeInfo.incPending(msg->getAddress()->getIndex());

        LOG(debug, "Sending message %s with id %zu to %s",
            msg->toString().c_str(),
            msg->getMsgId(),
            msg->getAddress()->toString().c_str());
    }
}

document::BucketId
PendingMessageTracker::reply(const api::StorageReply& r)
{
    vespalib::LockGuard guard(_lock);
    document::BucketId bucketId;

    LOG(debug, "Got reply: %s", r.toString().c_str());
    uint64_t msgId = r.getMsgId();

    MessagesByMsgId& msgs = boost::multi_index::get<0>(_messages);
    MessagesByMsgId::iterator iter = msgs.find(msgId);

    if (iter != msgs.end()) {
        bucketId = iter->bucketId;
        _nodeInfo.decPending(r.getAddress()->getIndex());
        updateNodeStatsOnReply(*iter);
        api::ReturnCode::Result code = r.getResult().getResult();
        if (code == api::ReturnCode::BUSY || code == api::ReturnCode::TIMEOUT) {
            _nodeInfo.setBusy(r.getAddress()->getIndex());
        }
        LOG(debug, "Erased message with id %zu", msgId);
        msgs.erase(msgId);
    }

    return bucketId;
}

void
PendingMessageTracker::updateNodeStatsOnReply(const MessageEntry& entry)
{
    NodeStats& stats(_nodeIndexToStats[entry.nodeIdx]);
    switch (entry.msgType) {
    case api::MessageType::PUT_ID:
        updateOperationStats(stats.puts, entry);
        break;
    default:
        return; // Message was for type not tracked by stats.
    }
}

void
PendingMessageTracker::updateOperationStats(OperationStats& opStats,
                                            const MessageEntry& entry) const
{
    // Time might go backwards due to clock adjustments (here assuming clock
    // implementation in storage framework is non-monotonic), so avoid negative
    // latencies by clamping to delta of 0.
    auto now = std::max(currentTime(), entry.timeStamp);
    opStats.totalLatency += (now - entry.timeStamp);
    ++opStats.numRequests;
}


NodeStatsSnapshot
PendingMessageTracker::getLatencyStatistics() const
{
    vespalib::LockGuard guard(_lock);
    NodeStatsSnapshot snapshot;
    // Conveniently, snapshot data structure is exactly the same as our own.
    snapshot.nodeToStats = _nodeIndexToStats;
    return snapshot;
}

NodeStatsSnapshot
PendingMessageTracker::ForwardingLatencyStatisticsProvider
::doGetLatencyStatistics() const
{
    return _messageTracker.getLatencyStatistics();
}

namespace {

template <typename Range>
void
runCheckerOnRange(PendingMessageTracker::Checker& checker, const Range& range)
{
    for (auto& e : range) {
        if (!checker.check(e.msgType, e.nodeIdx, e.priority)) {
            break;
        }
    }
}

}

void
PendingMessageTracker::checkPendingMessages(uint16_t node,
                                            const document::BucketId& bid,
                                            Checker& checker) const
{
    vespalib::LockGuard guard(_lock);
    const MessagesByNodeAndBucket& msgs(boost::multi_index::get<1>(_messages));

    auto range = pairAsRange(msgs.equal_range(boost::make_tuple(node, bid)));
    runCheckerOnRange(checker, range);
}

void
PendingMessageTracker::checkPendingMessages(const document::BucketId& bid,
                                            Checker& checker) const
{
    vespalib::LockGuard guard(_lock);
    const MessagesByBucketAndType& msgs(boost::multi_index::get<2>(_messages));

    auto range = pairAsRange(msgs.equal_range(boost::make_tuple(bid)));
    runCheckerOnRange(checker, range);
}

bool
PendingMessageTracker::hasPendingMessage(uint16_t node,
                                         const document::BucketId& bid,
                                         uint32_t messageType) const
{
    vespalib::LockGuard guard(_lock);
    const MessagesByNodeAndBucket& msgs(boost::multi_index::get<1>(_messages));

    auto range = msgs.equal_range(boost::make_tuple(node, bid, messageType));
    return (range.first != range.second);
}

void
PendingMessageTracker::getStatusStartPage(std::ostream& out) const
{
    out << "View:\n"
           "<ul>\n"
           "<li><a href=\"?order=bucket\">Group by bucket</a></li>"
           "<li><a href=\"?order=node\">Group by node</a></li>\n";
}

void
PendingMessageTracker::getStatusPerBucket(std::ostream& out) const
{
    vespalib::LockGuard guard(_lock);
    const MessagesByNodeAndBucket& msgs = boost::multi_index::get<1>(_messages);
    using BucketMap = std::map<document::BucketId,
                               std::vector<vespalib::string>>;
    BucketMap perBucketMsgs;
    for (auto& msg : msgs) {
        vespalib::asciistream ss;
        ss << "<li><i>Node "
           << msg.nodeIdx << "</i>: "
           << "<b>"
           << framework::MilliSecTime(msg.timeStamp.count()).toString()
           << "</b> "
           << msg.msgText << "</li>\n";

        perBucketMsgs[msg.bucketId].emplace_back(ss.str());
    }

    document::BucketId lastBucketId;
    for (auto& bucket : perBucketMsgs) {
        if (lastBucketId.getRawId() != 0) {
            out << "</ul>\n";
        }
        out << "<b>" << bucket.first << "</b>\n";
        out << "<ul>\n";
        lastBucketId = bucket.first;
        for (auto& msgDesc : bucket.second) {
            out << msgDesc;
        }
    }

    if (lastBucketId.getRawId() != 0) {
        out << "</ul>\n";
    }
}

void
PendingMessageTracker::getStatusPerNode(std::ostream& out) const
{
    vespalib::LockGuard guard(_lock);
    const MessagesByNodeAndBucket& msgs = boost::multi_index::get<1>(_messages);
    int lastNode = -1;
    for (MessagesByNodeAndBucket::const_iterator iter =
             msgs.begin(); iter != msgs.end(); iter++) {
        if (iter->nodeIdx != lastNode) {
            if (lastNode != -1) {
                out << "</ul>\n";
            }

            out << "<b>Node " << iter->nodeIdx
                << " (pending count: "
                << _nodeInfo.getPendingCount(iter->nodeIdx)
                << ")</b>\n<ul>\n";
            lastNode = iter->nodeIdx;
        }

        out << "<li><b>"
            << framework::MilliSecTime(iter->timeStamp.count()).toString()
            << "</b> "
            << iter->msgText << "</li>\n";
    }

    if (lastNode != -1) {
        out << "</ul>\n";
    }
}

void
PendingMessageTracker::reportHtmlStatus(
        std::ostream& out, const framework::HttpUrlPath& path) const
{
    if (!path.hasAttribute("order")) {
        getStatusStartPage(out);
    } else if (path.getAttribute("order") == "bucket") {
        getStatusPerBucket(out);
    } else if (path.getAttribute("order") == "node") {
        getStatusPerNode(out);
    }
}

void
PendingMessageTracker::print(std::ostream& /*out*/,
                             bool /*verbose*/,
                             const std::string& /*indent*/) const
{

}

NodeStats
PendingMessageTracker::getNodeStats(uint16_t node) const
{
    vespalib::LockGuard guard(_lock);
    auto nodeIter = _nodeIndexToStats.find(node);
    return (nodeIter != _nodeIndexToStats.end() ? nodeIter->second
                                                : NodeStats());
}

} // distributor
} // storage
