// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitoroperation.h"
#include <vespa/storage/storageserver/storagemetricsset.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/bucketownership.h>
#include <vespa/storage/distributor/operations/external/visitororder.h>
#include <vespa/storage/distributor/visitormetricsset.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/select/orderingselector.h>
#include <vespa/document/select/parser.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <iomanip>
#include <sstream>

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

vespalib::string
VisitorOperation::BucketInfo::toString() const
{
    vespalib::asciistream ost;
    print(ost);
    return ost.str();
}

VisitorOperation::VisitorOperation(
        DistributorComponent& owner,
        DistributorBucketSpace &bucketSpace,
        const api::CreateVisitorCommand::SP& m,
        const Config& config,
        VisitorMetricSet& metrics)
    : Operation(),
      _owner(owner),
      _bucketSpace(bucketSpace),
      _msg(m),
      _sentReply(false),
      _config(config),
      _metrics(metrics),
      _trace(TRACE_SOFT_MEMORY_LIMIT),
      _operationTimer(owner.getClock())
{
    const std::vector<document::BucketId>& buckets = m->getBuckets();

    if (buckets.size() > 0) {
        _superBucket = SuperBucketInfo(buckets[0]);
    }

    if (buckets.size() > 1) {
        _lastBucket = buckets[1];
    }

    _fromTime = m->getFromTime();
    _toTime = m->getToTime();
    if (_toTime == 0) {
        _toTime = owner.getUniqueTimestamp();
    }
}

VisitorOperation::~VisitorOperation()
{
}

document::BucketId
VisitorOperation::getLastBucketVisited()
{
    document::BucketId newLastBucket = _lastBucket;
    bool foundNotDone = false;
    bool foundDone = false;

    LOG(spam, "getLastBucketVisited(): Sub bucket count: %zu",
        _superBucket.subBucketsVisitOrder.size());
    for (uint32_t i=0; i<_superBucket.subBucketsVisitOrder.size(); i++) {
        auto found = _superBucket.subBuckets.find(_superBucket.subBucketsVisitOrder[i]);
        assert(found != _superBucket.subBuckets.end());
        LOG(spam, "%s => %s",
            found->first.toString().c_str(),
            found->second.toString().c_str());

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

uint64_t
VisitorOperation::timeLeft() const noexcept
{
    const auto elapsed = _operationTimer.getElapsedTime();
    framework::MilliSecTime timeSpent(
            std::chrono::duration_cast<std::chrono::milliseconds>(elapsed).count());

    LOG(spam,
        "Checking if visitor has timed out: elapsed=%zu ms, timeout=%u ms",
        timeSpent.getTime(),
        _msg->getTimeout());

    if (timeSpent.getTime() >= _msg->getTimeout()) {
        return 0;
    } else {
        return _msg->getTimeout() - timeSpent.getTime();
    }
}

void
VisitorOperation::markCompleted(const document::BucketId& bid,
                                const api::ReturnCode& code)
{
    VisitBucketMap::iterator found = _superBucket.subBuckets.find(bid);
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
                                  result.getMessage().c_str()));
}

void
VisitorOperation::onReceive(
        DistributorMessageSender& sender,
        const api::StorageReply::SP& r)
{
    api::CreateVisitorReply& reply = static_cast<api::CreateVisitorReply&>(*r);

    _trace.add(reply.getTrace().getRoot());

    SentMessagesMap::iterator iter = _sentMessages.find(reply.getMsgId());
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

    for (uint32_t i = 0; i < storageVisitor.getBuckets().size(); i++) {
        const document::BucketId& bid(storageVisitor.getBuckets()[i]);
        markCompleted(bid, result);
    }

    _sentMessages.erase(iter);
    startNewVisitors(sender);
}

namespace {

class VisitorVerificationException
{
public:
    VisitorVerificationException(api::ReturnCode::Result result,
                                 vespalib::stringref message)
        : _code(result, message)
    {}

    const api::ReturnCode& getReturnCode() const {
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
    if (_msg->getDocumentSelection().length() == 0
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
                lib::Node(lib::NodeType::DISTRIBUTOR, _owner.getIndex())));
    if (!ownState.getState().oneOf("ui")) {
        throw VisitorVerificationException(
                api::ReturnCode::ABORTED, "Distributor is shutting down");
    }
}

void
VisitorOperation::verifyDistributorOwnsBucket(const document::BucketId& bid)
{
    document::Bucket bucket(_msg->getBucketSpace(), bid);
    BucketOwnership bo(_owner.checkOwnershipInPendingAndCurrentState(bucket));
    if (!bo.isOwned()) {
        verifyDistributorIsNotDown(bo.getNonOwnedState());
        std::string systemStateStr = bo.getNonOwnedState().toString();
        LOG(debug,
            "Bucket %s is not owned by distributor %d, "
            "sending back system state '%s'",
            bid.toString().c_str(),
            _owner.getIndex(),
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

bool
VisitorOperation::verifyCreateVisitorCommand(DistributorMessageSender& sender)
{
    try {
        verifyOperationContainsBuckets();
        verifyOperationHasSuperbucketAndProgress();
        verifyOperationSentToCorrectDistributor();
        return true;
    } catch (const VisitorVerificationException& e) {
        LOG(debug,
            "Visitor verification failed; replying with %s",
            e.getReturnCode().toString().c_str());
        sendReply(e.getReturnCode(), sender);
        return false;
    }
}

namespace {

bool
isSplitPastOrderBits(const document::BucketId& bucket,
                     const document::OrderingSpecification& ordering) {
    int32_t bitsUsed = bucket.getUsedBits();
    int32_t orderBitCount = ordering.getWidthBits() -
                            ordering.getDivisionBits();
    return (bitsUsed > 32 + orderBitCount);
}

bool
isInconsistentlySplit(const document::BucketId& ain,
                      const document::BucketId& bin) {
    int minUsed = std::min(ain.getUsedBits(), bin.getUsedBits());

    document::BucketId a = document::BucketId(minUsed,
            ain.getRawId()).stripUnused();
    document::BucketId b = document::BucketId(minUsed,
            bin.getRawId()).stripUnused();

    return (a == b);
}

bool
isInconsistentlySplit(const document::BucketId& bucket,
                      const std::vector<document::BucketId>& buckets)
{
    if (buckets.size()) {
        for (uint32_t i=0; i<buckets.size(); i++) {
            if (isInconsistentlySplit(bucket, buckets[i])) {
                return true;
            }
        }
    }
    return false;
}

} // End anonymous namespace

bool
VisitorOperation::isSpecialBucketForOrderDoc(const document::BucketId& bucketId) const
{
    if (isSplitPastOrderBits(bucketId, *_ordering)) {
        LOG(spam, "Split past orderbits: Found in db: %s", bucketId.toString().c_str());
    } else if (isInconsistentlySplit(bucketId, _superBucket.subBucketsVisitOrder)) {
        LOG(spam, "Inconsistent: Found in db: %s", bucketId.toString().c_str());
    } else {
        return false;
    }
    return true;
}

std::vector<document::BucketId>::const_iterator
VisitorOperation::addSpecialBucketsForOrderDoc(
        std::vector<document::BucketId>::const_iterator iter,
        std::vector<document::BucketId>::const_iterator end)
{
    if (_ordering->getWidthBits() == 0) {
        return iter;
    }
    for (; iter != end; ++iter) {
        if (isSpecialBucketForOrderDoc(*iter)) {
            _superBucket.subBucketsVisitOrder.push_back(*iter);
            _superBucket.subBuckets[*iter] = BucketInfo();
        } else {
            break;
        }
    }
    return iter;
}

bool
VisitorOperation::pickBucketsToVisit(const std::vector<BucketDatabase::Entry>& buckets)
{
    uint32_t maxBuckets = _msg->getMaxBucketsPerVisitor();

    std::vector<document::BucketId> bucketVisitOrder;

    for (uint32_t i = 0; i < buckets.size(); ++i) {
        bucketVisitOrder.push_back(buckets[i].getBucketId());
    }

    VisitorOrder bucketLessThan(*_ordering);
    std::sort(bucketVisitOrder.begin(), bucketVisitOrder.end(), bucketLessThan);

    std::vector<document::BucketId>::const_iterator iter(bucketVisitOrder.begin());
    std::vector<document::BucketId>::const_iterator end(bucketVisitOrder.end());
    for (; iter != end; ++iter) {
        if (bucketLessThan(*iter, _lastBucket) ||
            *iter == _lastBucket)
        {
            LOG(spam,
                "Skipping bucket %s because it is lower than or equal to progress bucket %s",
                iter->toString().c_str(),
                _lastBucket.toString().c_str());
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

    iter = addSpecialBucketsForOrderDoc(iter, end);

    bool doneExpand(iter == bucketVisitOrder.end());
    return doneExpand;
}

bool
VisitorOperation::expandBucketAll()
{
    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getAll(_superBucket.bid, entries);
    return pickBucketsToVisit(entries);
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
    std::unique_ptr<document::BucketId> _next;

    NextEntryFinder(const document::BucketId& id)
        : _first(true), _last(id), _next() {}

    bool process(const BucketDatabase::Entry& e) override {
        document::BucketId bucket(e.getBucketId());

        if (_first && bucket == _last) {
            _first = false;
            return true;
        } else {
            _next.reset(new document::BucketId(bucket));
            return false;
        }
    }
};


std::unique_ptr<document::BucketId>
getBucketIdAndLast(
        BucketDatabase& database,
        const document::BucketId& super,
        const document::BucketId& last)
{
    if (!super.contains(last)) {
        NextEntryFinder proc(super);
        database.forEach(proc, super);
        return std::move(proc._next);
    } else {
        NextEntryFinder proc(last);
        database.forEach(proc, last);
        return std::move(proc._next);
    }
}

}

bool
VisitorOperation::expandBucketContained()
{
    uint32_t maxBuckets = _msg->getMaxBucketsPerVisitor();

    std::unique_ptr<document::BucketId> bid = getBucketIdAndLast(
            _bucketSpace.getBucketDatabase(),
            _superBucket.bid,
            _lastBucket);

    while (bid.get() && _superBucket.subBuckets.size() < maxBuckets) {
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

        bid = getBucketIdAndLast(_bucketSpace.getBucketDatabase(),
                                 _superBucket.bid,
                                 *bid);
    }

    bool doneExpand = (!bid.get() || !_superBucket.bid.contains(*bid));
    return doneExpand;
}

void
VisitorOperation::expandBucket()
{
    bool doneExpandBuckets = false;
    if (_ordering->getWidthBits() > 0) { // Orderdoc
        doneExpandBuckets = expandBucketAll();
    } else {
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

bool
alreadyTried(const std::vector<uint16_t>& triedNodes,
             uint16_t node)
{
    for (uint32_t j = 0; j < triedNodes.size(); j++) {
        if (triedNodes[j] == node) {
            return true;
        }
    }
    return false;
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

bool
VisitorOperation::documentSelectionMayHaveOrdering() const
{
    // FIXME: this is hairy and depends on opportunistic ordering
    // parsing working fine even when no ordering is present.
    return strcasestr(_msg->getDocumentSelection().c_str(), "order") != NULL;
}

void
VisitorOperation::attemptToParseOrderingSelector()
{
    std::unique_ptr<document::select::Node> docSelection;
    std::shared_ptr<document::DocumentTypeRepo> repo(_owner.getTypeRepo());
    document::select::Parser parser(
            *repo, _owner.getBucketIdFactory());
    docSelection = parser.parse(_msg->getDocumentSelection());
    
    document::OrderingSelector selector;
    _ordering = selector.select(*docSelection, _msg->getVisitorOrdering());
}

bool
VisitorOperation::parseDocumentSelection(DistributorMessageSender& sender)
{
    try{
        if (documentSelectionMayHaveOrdering()) {
            attemptToParseOrderingSelector();
        }

        if (!_ordering.get()) {
            _ordering.reset(new document::OrderingSpecification());
        }
    } catch (document::DocumentTypeNotFoundException& e) {
        std::ostringstream ost;
        ost << "Failed to parse document select string '"
            << _msg->getDocumentSelection() << "': " << e.getMessage();
        LOG(warning, "CreateVisitor(%s): %s",
            _msg->getInstanceId().c_str(), ost.str().c_str());

        sendReply(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, ost.str()), sender);
        return false;
    } catch (document::select::ParsingFailedException& e) {
        std::ostringstream ost;
        ost << "Failed to parse document select string '"
            << _msg->getDocumentSelection() << "': " << e.getMessage();
        LOG(warning, "CreateVisitor(%s): %s",
            _msg->getInstanceId().c_str(), ost.str().c_str());

        sendReply(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, ost.str()), sender);
        return false;
    }

    return true;
}

void
VisitorOperation::onStart(DistributorMessageSender& sender)
{
    if (!verifyCreateVisitorCommand(sender)) {
        return;
    }

    if (!parseDocumentSelection(sender)) {
        return;
    }

    expandBucket();

    startNewVisitors(sender);
}

bool
VisitorOperation::shouldAbortDueToTimeout() const noexcept
{
    return timeLeft() == 0;
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
VisitorOperation::startNewVisitors(DistributorMessageSender& sender)
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
                                    "Timeout of %u ms is running out",
                                    _msg->getTimeout())));
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
        LOG(debug,
            "Bucket %s does not exist anymore",
            entry.toString().c_str());
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
            LOG(spam,
                "Skipping subbucket %s because it is done/active/failed: %s",
                subBucket.toString().c_str(),
                bucketInfo.toString().c_str());
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
VisitorOperation::getNumVisitorsToSendForNode(uint16_t node,
                                              uint32_t totalBucketsOnNode) const
{
    int visitorCountAvailable(
            std::max(1, static_cast<int>(_config.maxVisitorsPerNodePerVisitor -
                                         _activeNodes[node])));

    int visitorCountMinBucketsPerVisitor(
            std::max(1, static_cast<int>(totalBucketsOnNode / _config.minBucketsPerVisitor)));

    int visitorCount(
            std::min(visitorCountAvailable, visitorCountMinBucketsPerVisitor));
    LOG(spam,
        "Will send %d visitors to node %d (available=%d, "
        "buckets restricted=%d)",
        visitorCount,
        node,
        visitorCountAvailable,
        visitorCountMinBucketsPerVisitor);

    return visitorCount;
}

bool
VisitorOperation::sendStorageVisitors(const NodeToBucketsMap& nodeToBucketsMap,
                                      DistributorMessageSender& sender)
{
    bool visitorsSent = false;
    for (NodeToBucketsMap::const_iterator iter = nodeToBucketsMap.begin();
         iter != nodeToBucketsMap.end();
         ++iter) {
        if (iter->second.size() > 0) {
            int visitorCount(getNumVisitorsToSendForNode(iter->first, iter->second.size()));

            std::vector<std::vector<document::BucketId> > bucketsVector(visitorCount);
            for (unsigned int i = 0; i < iter->second.size(); i++) {
                bucketsVector[i % visitorCount].push_back(iter->second[i]);
            }
            for (int i = 0; i < visitorCount; i++) {
                LOG(spam,
                    "Send visitor to node %d with %u buckets",
                    iter->first,
                    (unsigned int)bucketsVector[i].size());

                sendStorageVisitor(iter->first,
                                   bucketsVector[i],
                                   _msg->getMaximumPendingReplyCount(),
                                   sender);

                visitorsSent = true;
            }
        } else {
            LOG(spam, "Do not send visitor to node %d, no buckets", iter->first);
        }
    }
    return visitorsSent;
}

uint32_t
VisitorOperation::computeVisitorQueueTimeoutMs() const noexcept
{
    return timeLeft() / 2;
}

void
VisitorOperation::sendStorageVisitor(uint16_t node,
                                     const std::vector<document::BucketId>& buckets,
                                     uint32_t pending,
                                     DistributorMessageSender& sender)
{
    api::CreateVisitorCommand::SP cmd(new api::CreateVisitorCommand(*_msg));
    cmd->getBuckets() = buckets;

    // TODO: Send this through distributor - do after moving visitor stuff from docapi to storageprotocol
    cmd->setControlDestination(_msg->getControlDestination());
    cmd->setToTime(_toTime);

    vespalib::asciistream os;
    os << _msg->getInstanceId() << '-'
       << _owner.getIndex() << '-' << cmd->getMsgId();

    vespalib::string storageInstanceId(os.str());
    cmd->setInstanceId(storageInstanceId);
    cmd->setAddress(api::StorageMessageAddress(_owner.getClusterName(),
                                               lib::NodeType::STORAGE, node));
    cmd->setMaximumPendingReplyCount(pending);
    cmd->setQueueTimeout(computeVisitorQueueTimeoutMs());

    _sentMessages[cmd->getMsgId()] = cmd;

    cmd->setTimeout(timeLeft());

    LOG(spam, "Priority is %d", cmd->getPriority());
    LOG(debug, "Sending CreateVisitor command %zu for storage visitor '%s' to %s",
        cmd->getMsgId(),
        storageInstanceId.c_str(),
        cmd->getAddress()->toString().c_str());

    _activeNodes[node]++;
    sender.sendCommand(cmd);
}

void
VisitorOperation::sendReply(const api::ReturnCode& code, DistributorMessageSender& sender)
{
    if (!_sentReply) {
        // Send create visitor reply
        api::CreateVisitorReply::SP reply(new api::CreateVisitorReply(*_msg));
        _trace.moveTraceTo(reply->getTrace().getRoot());
        reply->setLastBucket(getLastBucketVisited());
        reply->setResult(code);

        reply->setVisitorStatistics(_visitorStatistics);
        LOG(debug,
            "Sending CreateVisitor reply %zu with return code '%s' for visitor "
            "'%s', msg id '%zu' back to client",
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
VisitorOperation::onClose(DistributorMessageSender& sender)
{
    sendReply(api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"),
              sender);
}

}
