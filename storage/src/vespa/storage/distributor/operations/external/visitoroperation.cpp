// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitoroperation.h"
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/storage/common/reindexing_constants.h>
#include <vespa/storage/storageserver/storagemetricsset.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/bucketownership.h>
#include <vespa/storage/distributor/operations/external/visitororder.h>
#include <vespa/storage/distributor/visitormetricsset.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <sstream>
#include <optional>

#include <vespa/log/log.h>
LOG_SETUP(".visitoroperation");

namespace storage::distributor {

void
VisitorOperation::BucketInfo::print(vespalib::asciistream & out) const
{
    out << "BucketInfo("
        << "done=" << done << ", "
        << "activeNode=" << activeNode <<  ", "
        << "failedCount=" << failedCount << ", "
        << "triedNodes=";
    for (uint32_t i = 0; i < triedNodes.size(); i++) {
        out << triedNodes[i];
        if (i != triedNodes.size()-1) {
            out << " ";
        }
    }
    out << ")";
}

VisitorOperation::BucketInfo::~BucketInfo() = default;

vespalib::string
VisitorOperation::BucketInfo::toString() const
{
    vespalib::asciistream ost;
    print(ost);
    return ost.str();
}

VisitorOperation::SuperBucketInfo::~SuperBucketInfo() = default;

namespace {

[[nodiscard]] bool
matches_visitor_library(vespalib::stringref input, vespalib::stringref expected)
{
    if (input.size() != expected.size()) {
        return false;
    }
    for (size_t i = 0; i < input.size(); ++i) {
        if (static_cast<char>(std::tolower(static_cast<unsigned char>(input[i]))) != expected[i]) {
            return false;
        }
    }
    return true;
}

}

VisitorOperation::VisitorOperation(
        const DistributorNodeContext& node_ctx,
        DistributorStripeOperationContext& op_ctx,
        DistributorBucketSpace &bucketSpace,
        const api::CreateVisitorCommand::SP& m,
        const Config& config,
        VisitorMetricSet& metrics)
    : Operation(),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _bucketSpace(bucketSpace),
      _msg(m),
      _config(config),
      _metrics(metrics),
      _trace(TRACE_SOFT_MEMORY_LIMIT),
      _operationTimer(_node_ctx.clock()),
      _bucket_lock(), // Initially no lock is held
      _sentReply(false),
      _verified_and_expanded(false),
      _is_read_for_write(matches_visitor_library(_msg->getLibraryName(), "reindexingvisitor"))
{
    const std::vector<document::BucketId>& buckets = m->getBuckets();

    if (!buckets.empty()) {
        _superBucket = SuperBucketInfo(buckets[0]);
    }

    if (buckets.size() > 1) {
        _lastBucket = buckets[1];
    }

    _fromTime = m->getFromTime();
    _toTime = m->getToTime();
    if (_toTime == 0) {
        _toTime = _op_ctx.generate_unique_timestamp();
    }
}

VisitorOperation::~VisitorOperation() = default;

document::BucketId
VisitorOperation::getLastBucketVisited()
{
    document::BucketId newLastBucket = _lastBucket;
    bool foundNotDone = false;
    bool foundDone = false;

    LOG(spam, "getLastBucketVisited(): Sub bucket count: %zu",
        _superBucket.subBucketsVisitOrder.size());
    for (const auto& sub_bucket : _superBucket.subBucketsVisitOrder) {
        auto found = _superBucket.subBuckets.find(sub_bucket);
        assert(found != _superBucket.subBuckets.end());
        LOG(spam, "%s => %s", found->first.toString().c_str(), found->second.toString().c_str());

        if (found->second.done) {
            foundDone = true;
        } else if (!allowInconsistencies()) {
            // Don't allow a non-complete bucket to be treated as successfully
            // visited unless we're doing an inconsistent visit.
            foundNotDone = true;
        }
        if (!foundNotDone) {
            newLastBucket = found->first;
        }
    }

    if (_superBucket.subBucketsCompletelyExpanded) {
        LOG(spam, "Sub buckets were completely expanded");
        if (_superBucket.subBucketsVisitOrder.empty()
            || (foundDone && !foundNotDone))
        {
            newLastBucket = document::BucketId(INT_MAX);
        }
    }

    LOG(spam, "Returning last bucket: %s", newLastBucket.toString().c_str());
    return newLastBucket;
}

vespalib::duration
VisitorOperation::timeLeft() const noexcept
{
    const auto elapsed = _operationTimer.getElapsedTime();

    LOG(spam, "Checking if visitor has timed out: elapsed=%" PRId64 " ms, timeout=%" PRId64 " ms",
        vespalib::count_ms(elapsed),
        vespalib::count_ms(_msg->getTimeout()));

    if (elapsed >= _msg->getTimeout()) {
        return vespalib::duration::zero();
    } else {
        return _msg->getTimeout() - elapsed;
    }
}

void
VisitorOperation::markCompleted(const document::BucketId& bid,
                                const api::ReturnCode& code)
{
    auto found = _superBucket.subBuckets.find(bid);
    assert(found != _superBucket.subBuckets.end());

    BucketInfo& info = found->second;
    assert(info.activeNode != -1);
    info.activeNode = -1;
    if (code.success()) {
        info.done = true;
    }
}

void
VisitorOperation::markOperationAsFailedDueToNodeError(
        const api::ReturnCode& result,
        uint16_t fromFailingNodeIndex)
{
    _storageError = api::ReturnCode(
            result.getResult(),
            vespalib::make_string("[from content node %u] %s",
                                  fromFailingNodeIndex,
                                  vespalib::string(result.getMessage()).c_str()));
}

void
VisitorOperation::onReceive(
        DistributorStripeMessageSender& sender,
        const api::StorageReply::SP& r)
{
    auto& reply = dynamic_cast<api::CreateVisitorReply&>(*r);

    _trace.add(reply.steal_trace());

    auto iter = _sentMessages.find(reply.getMsgId());
    assert(iter != _sentMessages.end());

    api::CreateVisitorCommand& storageVisitor = *iter->second;

    const uint16_t contentNodeIndex = storageVisitor.getAddress()->getIndex();
    _activeNodes[contentNodeIndex]--;

    api::ReturnCode result = reply.getResult();
    if (result.success()) {
        _visitorStatistics = _visitorStatistics + reply.getVisitorStatistics();
        LOG(spam, "Client stats %s for visitor %s. New stats is %s",
            reply.getVisitorStatistics().toString().c_str(),
            _msg->getInstanceId().c_str(),
            _visitorStatistics.toString().c_str());
    } else if (result.isCriticalForVisitorDispatcher()) {
        // If an error code is critical, we don't bother to do a "worst-of"
        // comparison with the existing code since it's assumed either one is
        // sufficiently bad to tell the client about it.
         markOperationAsFailedDueToNodeError(result, contentNodeIndex);
    }
    // else: will lose code for non-critical events, degenerates to "not found".

    for (const auto& bucket : storageVisitor.getBuckets()) {
        markCompleted(bucket, result);
    }

    _sentMessages.erase(iter);
    startNewVisitors(sender);
}

namespace {

class VisitorVerificationException {
public:
    VisitorVerificationException(api::ReturnCode::Result result,
                                 vespalib::stringref message)
        : _code(result, message)
    {}

    const api::ReturnCode& getReturnCode() const noexcept {
        return _code;
    }

private:
    api::ReturnCode _code;
};

}

void
VisitorOperation::verifyDistributorsAreAvailable()
{
    const lib::ClusterState& clusterState = _bucketSpace.getClusterState();
    if (clusterState.getNodeCount(lib::NodeType::DISTRIBUTOR) == 0) {
        vespalib::string err(vespalib::make_string(
            "No distributors available when processing visitor '%s'",
            _msg->getInstanceId().c_str()));
        LOG(debug, "%s", err.c_str());
        throw VisitorVerificationException(api::ReturnCode::NOT_READY, err);
    }
}

void
VisitorOperation::verifyVisitorDistributionBitCount(
        const document::BucketId& bid)
{
    const lib::ClusterState& clusterState = _bucketSpace.getClusterState();
    if (_msg->getDocumentSelection().empty()
        && bid.getUsedBits() != clusterState.getDistributionBitCount())
    {
        LOG(debug,
            "Got message with wrong distribution bits (%d != %d), bucketid %s, "
            "sending back system state '%s'",
            bid.getUsedBits(),
            clusterState.getDistributionBitCount(),
            bid.toString().c_str(),
            clusterState.toString().c_str());
        throw VisitorVerificationException(
                api::ReturnCode::WRONG_DISTRIBUTION,
                clusterState.toString());
    }
}

void
VisitorOperation::verifyDistributorIsNotDown(const lib::ClusterState& state)
{
    const lib::NodeState& ownState(
            state.getNodeState(
                lib::Node(lib::NodeType::DISTRIBUTOR, _node_ctx.node_index())));
    if (!ownState.getState().oneOf("ui")) {
        throw VisitorVerificationException(
                api::ReturnCode::ABORTED, "Distributor is shutting down");
    }
}

void
VisitorOperation::verifyDistributorOwnsBucket(const document::BucketId& bid)
{
    auto &bucket_space(_op_ctx.bucket_space_repo().get(_msg->getBucketSpace()));
    BucketOwnership bo(bucket_space.check_ownership_in_pending_and_current_state(bid));
    if (!bo.isOwned()) {
        verifyDistributorIsNotDown(bo.getNonOwnedState());
        std::string systemStateStr = bo.getNonOwnedState().toString();
        LOG(debug,
            "Bucket %s is not owned by distributor %d, "
            "sending back system state '%s'",
            bid.toString().c_str(),
            _node_ctx.node_index(),
            bo.getNonOwnedState().toString().c_str());
        throw VisitorVerificationException(
                api::ReturnCode::WRONG_DISTRIBUTION,
                bo.getNonOwnedState().toString());
    }
}

void
VisitorOperation::verifyOperationContainsBuckets()
{
    size_t bucketCount = _msg->getBuckets().size();
    if (bucketCount == 0) {
        vespalib::string errorMsg = vespalib::make_string(
                "No buckets in CreateVisitorCommand for visitor '%s'",
                _msg->getInstanceId().c_str());
        throw VisitorVerificationException(api::ReturnCode::ILLEGAL_PARAMETERS, errorMsg);
    }
}

void
VisitorOperation::verifyOperationHasSuperbucketAndProgress()
{
    size_t bucketCount = _msg->getBuckets().size();
    if (bucketCount != 2) {
        vespalib::string errorMsg = vespalib::make_string(
                "CreateVisitorCommand does not contain 2 buckets for visitor '%s'",
                _msg->getInstanceId().c_str());
        throw VisitorVerificationException(api::ReturnCode::ILLEGAL_PARAMETERS, errorMsg);
    }
}

void
VisitorOperation::verifyOperationSentToCorrectDistributor()
{
    verifyDistributorsAreAvailable();
    verifyVisitorDistributionBitCount(_superBucket.bid);
    verifyDistributorOwnsBucket(_superBucket.bid);
}

void
VisitorOperation::verify_fieldset_makes_sense_for_visiting()
{
    if (_msg->getFieldSet() == document::NoFields::NAME) {
        throw VisitorVerificationException(
                api::ReturnCode::ILLEGAL_PARAMETERS,
                "Field set '[none]' is not supported for external visitor operations. "
                "Use '[id]' to return documents with no fields set.");
    }
}

bool
VisitorOperation::verifyCreateVisitorCommand(DistributorStripeMessageSender& sender)
{
    try {
        verifyOperationContainsBuckets();
        verifyOperationHasSuperbucketAndProgress();
        verifyOperationSentToCorrectDistributor();
        verify_fieldset_makes_sense_for_visiting();
        // TODO wrap and test
        if (is_read_for_write() && (_msg->getMaxBucketsPerVisitor() != 1)) {
            throw VisitorVerificationException(
                    api::ReturnCode::ILLEGAL_PARAMETERS,
                    vespalib::make_string("Read-for-write visitors can only have 1 max pending bucket, was %u",
                                          _msg->getMaxBucketsPerVisitor()));
        }
        return true;
    } catch (const VisitorVerificationException& e) {
        LOG(debug,
            "Visitor verification failed; replying with %s",
            e.getReturnCode().toString().c_str());
        sendReply(e.getReturnCode(), sender);
        return false;
    }
}

bool
VisitorOperation::pickBucketsToVisit(const std::vector<BucketDatabase::Entry>& buckets)
{
    uint32_t maxBuckets = _msg->getMaxBucketsPerVisitor();

    std::vector<document::BucketId> bucketVisitOrder;

    for (const auto& bucket : buckets) {
        bucketVisitOrder.push_back(bucket.getBucketId());
    }

    VisitorOrder bucketLessThan;
    std::sort(bucketVisitOrder.begin(), bucketVisitOrder.end(), bucketLessThan);

    auto iter = bucketVisitOrder.begin();
    auto end  = bucketVisitOrder.end();
    for (; iter != end; ++iter) {
        if (bucketLessThan(*iter, _lastBucket) || *iter == _lastBucket) {
            LOG(spam, "Skipping bucket %s because it is lower than or equal to progress bucket %s",
                iter->toString().c_str(), _lastBucket.toString().c_str());
            continue;
        }
        LOG(spam, "Iterating: Found in db: %s", iter->toString().c_str());
        _superBucket.subBucketsVisitOrder.push_back(*iter);
        _superBucket.subBuckets[*iter] = BucketInfo();
        if (_superBucket.subBuckets.size() >= maxBuckets) {
            ++iter;
            break;
        }
    }

    bool doneExpand(iter == bucketVisitOrder.end());
    return doneExpand;
}

bool
VisitorOperation::expandBucketContaining()
{
    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getParents(_superBucket.bid, entries);
    return pickBucketsToVisit(entries);
}

namespace {

struct NextEntryFinder : public BucketDatabase::EntryProcessor {
    bool _first;
    document::BucketId _last;
    std::optional<document::BucketId> _next;

    explicit NextEntryFinder(const document::BucketId& id) noexcept
        : _first(true), _last(id), _next()
    {}

    bool process(const BucketDatabase::ConstEntryRef& e) override {
        document::BucketId bucket(e.getBucketId());

        if (_first && bucket == _last) {
            _first = false;
            return true;
        } else {
            _next.emplace(bucket);
            return false;
        }
    }
};


std::optional<document::BucketId>
getBucketIdAndLast(BucketDatabase& database,
                   const document::BucketId& super,
                   const document::BucketId& last)
{
    if (!super.contains(last)) {
        NextEntryFinder proc(super);
        database.for_each_upper_bound(proc, super);
        return proc._next;
    } else {
        NextEntryFinder proc(last);
        database.for_each_upper_bound(proc, last);
        return proc._next;
    }
}

}

bool
VisitorOperation::expandBucketContained()
{
    uint32_t maxBuckets = _msg->getMaxBucketsPerVisitor();

    std::optional<document::BucketId> bid = getBucketIdAndLast(
            _bucketSpace.getBucketDatabase(),
            _superBucket.bid,
            _lastBucket);

    while (bid.has_value() && _superBucket.subBuckets.size() < maxBuckets) {
        if (!_superBucket.bid.contains(*bid)) {
            LOG(spam,
                "Iterating: Found bucket %s is not contained in bucket %s",
                bid->toString().c_str(),
                _superBucket.bid.toString().c_str());
            break;
        }

        LOG(spam, "Iterating: Found in db: %s", bid->toString().c_str());
        _superBucket.subBucketsVisitOrder.push_back(*bid);
        _superBucket.subBuckets[*bid] = BucketInfo();

        bid = getBucketIdAndLast(_bucketSpace.getBucketDatabase(), _superBucket.bid, *bid);
    }

    bool doneExpand = (!bid.has_value() || !_superBucket.bid.contains(*bid));
    return doneExpand;
}

void
VisitorOperation::expandBucket()
{
    bool doneExpandBuckets = false;
    bool doneExpandContainingBuckets = true;
    if (!_superBucket.bid.contains(_lastBucket)) {
        LOG(spam, "Bucket %s does not contain progress bucket %s",
            _superBucket.bid.toString().c_str(),
            _lastBucket.toString().c_str());
        doneExpandContainingBuckets = expandBucketContaining();
    } else {
        LOG(spam, "Bucket %s contains progress bucket %s",
            _superBucket.bid.toString().c_str(),
            _lastBucket.toString().c_str());
    }

    if (doneExpandContainingBuckets) {
        LOG(spam, "Done expanding containing buckets");
        doneExpandBuckets = expandBucketContained();
    }

    if (doneExpandBuckets) {
        _superBucket.subBucketsCompletelyExpanded = true;
        LOG(spam,
            "Sub buckets completely expanded for super bucket %s",
            _superBucket.bid.toString().c_str());
    } else {
        LOG(spam,
            "Sub buckets NOT completely expanded for super bucket %s",
            _superBucket.bid.toString().c_str());
    }
}

namespace {

[[nodiscard]] bool alreadyTried(const std::vector<uint16_t>& triedNodes, uint16_t node) noexcept {
    return std::find(triedNodes.begin(), triedNodes.end(), node) != triedNodes.end();
}

int
findNodeWithMostDocuments(const std::vector<BucketCopy>& potentialNodes)
{
    int indexWithMostDocs = -1;
    for (uint32_t i = 0; i < potentialNodes.size(); i++) {
        if (indexWithMostDocs == -1 ||
            potentialNodes[i].getDocumentCount() >
            potentialNodes[indexWithMostDocs].getDocumentCount())
        {
            indexWithMostDocs = i;
        }
    }
    return potentialNodes[indexWithMostDocs].getNode();
}

}

int
VisitorOperation::pickTargetNode(
        const BucketDatabase::Entry& entry,
        const std::vector<uint16_t>& triedNodes)
{
    std::vector<BucketCopy> potentialNodes;

    // Figure out if there are any trusted nodes. If there are,
    // only those should be considered for visiting.
    bool foundTrusted = entry->hasTrusted();
    for (uint32_t i = 0; i < entry->getNodeCount(); i++) {
        const BucketCopy& copy(entry->getNodeRef(i));
        if (foundTrusted && !copy.trusted()) {
            continue;
        }
        if (!alreadyTried(triedNodes, copy.getNode())) {
            potentialNodes.push_back(copy);
        }
    }

    if (potentialNodes.empty()) {
        return -1;
    }

    if (!entry->validAndConsistent()) {
        return findNodeWithMostDocuments(potentialNodes);
    }
    
    assert(!potentialNodes.empty());
    return potentialNodes.front().getNode();
}

void
VisitorOperation::onStart(DistributorStripeMessageSender& sender)
{
    if (!_verified_and_expanded) {
        if (!verify_command_and_expand_buckets(sender)) {
            return;
        }
    }
    startNewVisitors(sender);
}

bool
VisitorOperation::verify_command_and_expand_buckets(DistributorStripeMessageSender& sender)
{
    assert(!_verified_and_expanded);
    _verified_and_expanded = true;
    if (!verifyCreateVisitorCommand(sender)) {
        return false;
    }
    expandBucket();
    return true;
}

bool
VisitorOperation::shouldAbortDueToTimeout() const noexcept
{
    return timeLeft() <= vespalib::duration::zero();
}

void
VisitorOperation::markOperationAsFailed(const api::ReturnCode& result)
{
    // Error codes are ordered so that increasing numbers approximate
    // increasing severity. In particular, transient errors < fatal errors.
    // In case of same error code, don't overwrite initial error.
    if (_storageError.getResult() < result.getResult()) {
        _storageError = result;
    }
}

bool
VisitorOperation::maySendNewStorageVisitors() const noexcept
{
    // If we've already failed, don't bother sending any more visitors.
    // We rather want to get all currently pending visitors done so
    // we can send a timely reply back to the visiting client.
    return _storageError.success();
}

void
VisitorOperation::startNewVisitors(DistributorStripeMessageSender& sender)
{
    LOG(spam,
        "Starting new visitors: Superbucket: %s, last subbucket: %s",
        _superBucket.bid.toString().c_str(),
        _lastBucket.toString().c_str());

    initializeActiveNodes();

    NodeToBucketsMap nodeToBucketsMap;
    if (!assignBucketsToNodes(nodeToBucketsMap)
        && !allowInconsistencies()
        && _storageError.success())
    {
        // We do not allow "not found" to override any other errors.
        // Furthermore, we do not fail with not found if we're visiting with
        // inconsistencies allowed.
        markOperationAsFailed(
                api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND));
    }
    if (shouldAbortDueToTimeout()) {
        markOperationAsFailed(
                api::ReturnCode(api::ReturnCode::ABORTED,
                                vespalib::make_string(
                                    "Timeout of %" PRId64 " ms is running out",
                                    vespalib::count_ms(_msg->getTimeout()))));
    }

    if (maySendNewStorageVisitors()) {
        sendStorageVisitors(nodeToBucketsMap, sender);
    }

    if (_sentMessages.empty()) {
        sendReply(_storageError, sender);
    }
}

void
VisitorOperation::initializeActiveNodes()
{
    const lib::ClusterState& clusterState(_bucketSpace.getClusterState());

    uint32_t storageNodeCount = clusterState.getNodeCount(lib::NodeType::STORAGE);
    if (storageNodeCount > _activeNodes.size()) {
        _activeNodes.resize(storageNodeCount);
    }
}

bool
VisitorOperation::shouldSkipBucket(const BucketInfo& bucketInfo) const
{
    return (bucketInfo.done ||
            bucketInfo.activeNode != -1 ||
            bucketInfo.failedCount > 0);
}

bool
VisitorOperation::bucketIsValidAndConsistent(const BucketDatabase::Entry& entry) const
{
    if (!entry.valid()) {
        LOG(debug, "Bucket %s does not exist anymore", entry.toString().c_str());
        return false;
    }
    assert(entry->getNodeCount() != 0);

    if (!allowInconsistencies() && !entry->hasTrusted()) {
        LOG(spam,
            "Failing visitor because %s is currently inconsistent. "
            "Bucket contents: %s",
            entry.getBucketId().toString().c_str(),
            entry->toString().c_str());
        return false;
    }

    return true;
}

bool
VisitorOperation::allowInconsistencies() const noexcept
{
    return _msg->visitInconsistentBuckets();
}

bool
VisitorOperation::assignBucketsToNodes(NodeToBucketsMap& nodeToBucketsMap)
{
    for (const auto& subBucket : _superBucket.subBucketsVisitOrder) {
        auto subIter(_superBucket.subBuckets.find(subBucket));
        assert(subIter != _superBucket.subBuckets.end());

        BucketInfo& bucketInfo(subIter->second);
        if (shouldSkipBucket(bucketInfo)) {
            LOG(spam, "Skipping subbucket %s because it is done/active/failed: %s",
                subBucket.toString().c_str(), bucketInfo.toString().c_str());
            continue;
        }

        BucketDatabase::Entry entry(_bucketSpace.getBucketDatabase().get(subBucket));
        if (!bucketIsValidAndConsistent(entry)) {
            return false;
        }

        int node = pickTargetNode(entry, bucketInfo.triedNodes);
        if (node == -1) {
            return false;
        }
        LOG(spam, "Visiting %s on node %d", subBucket.toString().c_str(), node);
        bucketInfo.activeNode = node;
        bucketInfo.triedNodes.push_back(node);
        nodeToBucketsMap[node].push_back(subBucket);
    }
    return true;
}

int
VisitorOperation::getNumVisitorsToSendForNode(uint16_t node, uint32_t totalBucketsOnNode) const
{
    int visitorCountAvailable(std::max(1, static_cast<int>(_config.maxVisitorsPerNodePerVisitor - _activeNodes[node])));
    int visitorCountMinBucketsPerVisitor(std::max(1, static_cast<int>(totalBucketsOnNode / _config.minBucketsPerVisitor)));
    int visitorCount(std::min(visitorCountAvailable, visitorCountMinBucketsPerVisitor));
    LOG(spam, "Will send %d visitors to node %d (available=%d, buckets restricted=%d)",
        visitorCount, node, visitorCountAvailable, visitorCountMinBucketsPerVisitor);

    return visitorCount;
}

bool
VisitorOperation::sendStorageVisitors(const NodeToBucketsMap& nodeToBucketsMap,
                                      DistributorStripeMessageSender& sender)
{
    bool visitorsSent = false;
    for (const auto & entry : nodeToBucketsMap ) {
        if (entry.second.size() > 0) {
            int visitorCount(getNumVisitorsToSendForNode(entry.first, entry.second.size()));

            std::vector<std::vector<document::BucketId> > bucketsVector(visitorCount);
            for (unsigned int i = 0; i < entry.second.size(); i++) {
                bucketsVector[i % visitorCount].push_back(entry.second[i]);
            }
            for (int i = 0; i < visitorCount; i++) {
                LOG(spam, "Send visitor to node %d with %u buckets",
                    entry.first, (unsigned int)bucketsVector[i].size());

                sendStorageVisitor(entry.first, bucketsVector[i], _msg->getMaximumPendingReplyCount(), sender);

                visitorsSent = true;
            }
        } else {
            LOG(spam, "Do not send visitor to node %d, no buckets", entry.first);
        }
    }
    return visitorsSent;
}

vespalib::duration
VisitorOperation::computeVisitorQueueTimeoutMs() const noexcept
{
    return timeLeft() / 2;
}

void
VisitorOperation::sendStorageVisitor(uint16_t node,
                                     const std::vector<document::BucketId>& buckets,
                                     uint32_t pending,
                                     DistributorStripeMessageSender& sender)
{
    // TODO rewrite to not use copy ctor and remove wonky StorageCommand copy ctor impl
    auto cmd = std::make_shared<api::CreateVisitorCommand>(*_msg);
    cmd->getBuckets() = buckets;

    // TODO: Send this through distributor - do after moving visitor stuff from docapi to storageprotocol
    cmd->setControlDestination(_msg->getControlDestination());
    cmd->setToTime(_toTime);

    vespalib::asciistream os;
    os << _msg->getInstanceId() << '-'
       << _node_ctx.node_index() << '-' << cmd->getMsgId();

    vespalib::string storageInstanceId(os.str());
    cmd->setInstanceId(storageInstanceId);
    cmd->setAddress(api::StorageMessageAddress::create(_node_ctx.cluster_name_ptr(), lib::NodeType::STORAGE, node));
    cmd->setMaximumPendingReplyCount(pending);
    cmd->setQueueTimeout(computeVisitorQueueTimeoutMs());

    _sentMessages[cmd->getMsgId()] = cmd;

    cmd->setTimeout(timeLeft());

    if (!_put_lock_token.empty()) {
        cmd->getParameters().set(reindexing_bucket_lock_visitor_parameter_key(), _put_lock_token);
    }

    LOG(spam, "Priority is %d", cmd->getPriority());
    LOG(debug, "Sending CreateVisitor command %" PRIu64 " for storage visitor '%s' to %s",
        cmd->getMsgId(),
        storageInstanceId.c_str(),
        cmd->getAddress()->toString().c_str());

    _activeNodes[node]++;
    sender.sendCommand(cmd);
}

void
VisitorOperation::sendReply(const api::ReturnCode& code, DistributorStripeMessageSender& sender)
{
    if (!_sentReply) {
        // Send create visitor reply
        auto reply = std::make_shared<api::CreateVisitorReply>(*_msg);
        _trace.moveTraceTo(reply->getTrace());
        reply->setLastBucket(getLastBucketVisited());
        reply->setResult(code);

        reply->setVisitorStatistics(_visitorStatistics);
        LOG(debug,
            "Sending CreateVisitor reply %" PRIu64 " with return code '%s' for visitor "
            "'%s', msg id '%" PRIu64 "' back to client",
            reply->getMsgId(),
            code.toString().c_str(),
            _msg->getInstanceId().c_str(), _msg->getMsgId());

        updateReplyMetrics(code);
        sender.sendReply(reply);

        _sentReply = true;
    }
}

void
VisitorOperation::updateReplyMetrics(const api::ReturnCode& result)
{
    _metrics.updateFromResult(result);
    // WrongDistributionReply happens as a normal and expected part of a visitor
    // session's lifetime. If we pollute the metrics with measurements taken
    // from such replies, the averages will not be representative.
    if (result.getResult() == api::ReturnCode::WRONG_DISTRIBUTION) {
        return;
    }
    _metrics.latency.addValue(_operationTimer.getElapsedTimeAsDouble());
    _metrics.buckets_per_visitor.addValue(_visitorStatistics.getBucketsVisited());
    _metrics.docs_per_visitor.addValue(_visitorStatistics.getDocumentsVisited());
    _metrics.bytes_per_visitor.addValue(_visitorStatistics.getBytesVisited());
}

void
VisitorOperation::onClose(DistributorStripeMessageSender& sender)
{
    sendReply(api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"), sender);
}

void
VisitorOperation::fail_with_bucket_already_locked(DistributorStripeMessageSender& sender)
{
    assert(is_read_for_write());
    sendReply(api::ReturnCode(api::ReturnCode::BUSY, "This bucket is already locked by another operation"), sender);
}

void
VisitorOperation::fail_with_merge_pending(DistributorStripeMessageSender& sender)
{
    assert(is_read_for_write());
    sendReply(api::ReturnCode(api::ReturnCode::BUSY, "A merge operation is pending for this bucket"), sender);
}

std::optional<document::Bucket>
VisitorOperation::first_bucket_to_visit() const
{
    if (_superBucket.subBuckets.empty()) {
        return {};
    }
    return {document::Bucket(_msg->getBucketSpace(), _superBucket.subBuckets.begin()->first)};
}

void
VisitorOperation::assign_bucket_lock_handle(SequencingHandle handle)
{
    _bucket_lock = std::move(handle);
}

void
VisitorOperation::assign_put_lock_access_token(const vespalib::string& token)
{
    _put_lock_token = token;
}

}
