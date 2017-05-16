// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdbupdater.h"
#include "distributor.h"
#include "simpleclusterinformation.h"
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/multioperation.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.bucketdb.updater");

using storage::lib::Node;
using storage::lib::NodeType;

namespace storage::distributor {

BucketDBUpdater::BucketDBUpdater(Distributor& owner, ManagedBucketSpace& bucketSpace,
                                 DistributorMessageSender& sender, DistributorComponentRegister& compReg)
    : framework::StatusReporter("bucketdb", "Bucket DB Updater"),
      _bucketSpaceComponent(owner, bucketSpace, compReg, "Bucket DB Updater"),
      _sender(sender),
      _transitionTimer(_bucketSpaceComponent.getClock())
{
}

BucketDBUpdater::~BucketDBUpdater() {}

void
BucketDBUpdater::flush()
{
    for (std::map<uint64_t, BucketRequest>::iterator
             i(_sentMessages.begin()), end(_sentMessages.end());
         i != end; ++i)
    {
        // Cannot sendDown MergeBucketReplies during flushing, since
        // all lower links have been closed
        if (i->second._mergeReplyGuard.get()) {
            i->second._mergeReplyGuard->resetReply();
        }
    }
    _sentMessages.clear();
}

void
BucketDBUpdater::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "BucketDBUpdater";
}

bool
BucketDBUpdater::hasPendingClusterState() const
{
    return _pendingClusterState.get() != nullptr;
}

BucketOwnership
BucketDBUpdater::checkOwnershipInPendingState(const document::BucketId& b) const
{
    if (hasPendingClusterState()) {
        const lib::ClusterState& state(
                _pendingClusterState->getNewClusterState());
        const lib::Distribution& distribution(
                _pendingClusterState->getDistribution());
        if (!_bucketSpaceComponent.ownsBucketInState(distribution, state, b)) {
            return BucketOwnership::createNotOwnedInState(state);
        }
    }
    return BucketOwnership::createOwned();
}

void
BucketDBUpdater::clearPending(uint16_t node)
{
    for (std::map<uint64_t, BucketRequest>::iterator iter(
            _sentMessages.begin()); iter != _sentMessages.end();)
    {
        if (iter->second.targetNode == node) {
            std::map<uint64_t, BucketRequest>::iterator del = iter;
            iter++;
            _sentMessages.erase(del);
        } else {
            iter++;
        }
    }
}

void
BucketDBUpdater::sendRequestBucketInfo(
        uint16_t node,
        const document::BucketId& bucket,
        const std::shared_ptr<MergeReplyGuard>& mergeReplyGuard)
{
    if (!_bucketSpaceComponent.storageNodeIsUp(node)) {
        return;
    }

    std::vector<document::BucketId> buckets;
    buckets.push_back(bucket);

    std::shared_ptr<api::RequestBucketInfoCommand> msg(
            new api::RequestBucketInfoCommand(buckets));

    LOG(debug,
        "Sending request bucket info command %lu for "
        "bucket %s to node %u",
        msg->getMsgId(),
        bucket.toString().c_str(),
        node);

    msg->setPriority(50);
    msg->setAddress(_bucketSpaceComponent.nodeAddress(node));

    _sentMessages[msg->getMsgId()] =
        BucketRequest(node, _bucketSpaceComponent.getUniqueTimestamp(),
                      bucket, mergeReplyGuard);
    _sender.sendCommand(msg);
}

void
BucketDBUpdater::recheckBucketInfo(uint32_t nodeIdx,
                                   const document::BucketId& bid)
{
    sendRequestBucketInfo(nodeIdx, bid, std::shared_ptr<MergeReplyGuard>());
}

void
BucketDBUpdater::removeSuperfluousBuckets(
        const lib::Distribution& newDistribution,
        const lib::ClusterState& newState)
{
    // Remove all buckets not belonging to this distributor, or
    // being on storage nodes that are no longer up.
    NodeRemover proc(
            _bucketSpaceComponent.getClusterState(),
            newState,
            _bucketSpaceComponent.getBucketIdFactory(),
            _bucketSpaceComponent.getIndex(),
            newDistribution,
            _bucketSpaceComponent.getDistributor().getStorageNodeUpStates());

    _bucketSpaceComponent.getBucketDatabase().forEach(proc);

    for (uint32_t i = 0; i < proc.getBucketsToRemove().size(); ++i) {
        _bucketSpaceComponent.getBucketDatabase()
                .remove(proc.getBucketsToRemove()[i]);
    }
}

void
BucketDBUpdater::ensureTransitionTimerStarted()
{
    // Don't overwrite start time if we're already processing a state, as
    // that will make transition times appear artificially low.
    if (!hasPendingClusterState()) {
        _transitionTimer = framework::MilliSecTimer(
                _bucketSpaceComponent.getClock());
    }
}

void
BucketDBUpdater::completeTransitionTimer()
{
    _bucketSpaceComponent.getDistributor().getMetrics()
            .stateTransitionTime.addValue(_transitionTimer.getElapsedTimeAsDouble());
}

void
BucketDBUpdater::storageDistributionChanged(
        const lib::Distribution& distribution)
{
    ensureTransitionTimerStarted();

    removeSuperfluousBuckets(distribution,
            _bucketSpaceComponent.getClusterState());

    ClusterInformation::CSP clusterInfo(new SimpleClusterInformation(
            _bucketSpaceComponent.getIndex(),
            distribution,
            _bucketSpaceComponent.getClusterState(),
            _bucketSpaceComponent.getDistributor().getStorageNodeUpStates()));
    _pendingClusterState = PendingClusterState::createForDistributionChange(
            _bucketSpaceComponent.getClock(),
            std::move(clusterInfo),
            _sender,
            _bucketSpaceComponent.getUniqueTimestamp());
    _outdatedNodes = _pendingClusterState->getOutdatedNodeSet();
}

void
BucketDBUpdater::replyToPreviousPendingClusterStateIfAny()
{
    if (_pendingClusterState.get() &&
        _pendingClusterState->getCommand().get())
    {
        _bucketSpaceComponent.sendUp(
                std::make_shared<api::SetSystemStateReply>(*_pendingClusterState->getCommand()));
    }
}

bool
BucketDBUpdater::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    LOG(debug,
        "Received new cluster state %s",
        cmd->getSystemState().toString().c_str());

    lib::ClusterState oldState = _bucketSpaceComponent.getClusterState();
    const lib::ClusterState& state = cmd->getSystemState();

    if (state == oldState) {
        return false;
    }
    ensureTransitionTimerStarted();

    removeSuperfluousBuckets(
            _bucketSpaceComponent.getDistribution(),
            cmd->getSystemState());
    replyToPreviousPendingClusterStateIfAny();

    ClusterInformation::CSP clusterInfo(
            new SimpleClusterInformation(
                _bucketSpaceComponent.getIndex(),
                _bucketSpaceComponent.getDistribution(),
                _bucketSpaceComponent.getClusterState(),
                _bucketSpaceComponent.getDistributor()
                .getStorageNodeUpStates()));
    _pendingClusterState = PendingClusterState::createForClusterStateChange(
            _bucketSpaceComponent.getClock(),
            std::move(clusterInfo),
            _sender,
            cmd,
            _outdatedNodes,
            _bucketSpaceComponent.getUniqueTimestamp());
    _outdatedNodes = _pendingClusterState->getOutdatedNodeSet();

    if (isPendingClusterStateCompleted()) {
        processCompletedPendingClusterState();
    }
    return true;
}

BucketDBUpdater::MergeReplyGuard::~MergeReplyGuard()
{
    if (_reply.get()) {
        _updater.getDistributorComponent().getDistributor()
                .handleCompletedMerge(_reply);
    }
}

bool
BucketDBUpdater::onMergeBucketReply(
        const std::shared_ptr<api::MergeBucketReply>& reply)
{
   std::shared_ptr<MergeReplyGuard> replyGuard(
           new MergeReplyGuard(*this, reply));

   // In case the merge was unsuccessful somehow, or some nodes weren't
   // actually merged (source-only nodes?) we request the bucket info of the
   // bucket again to make sure it's ok.
   for (uint32_t i = 0; i < reply->getNodes().size(); i++) {
       sendRequestBucketInfo(reply->getNodes()[i].index,
                             reply->getBucketId(),
                             replyGuard);
   }

   return true;
}

void
BucketDBUpdater::enqueueRecheckUntilPendingStateEnabled(
        uint16_t node,
        const document::BucketId& bucket)
{
    LOG(spam,
        "DB updater has a pending cluster state, enqueuing recheck "
        "of bucket %s on node %u until state is done processing",
        bucket.toString().c_str(),
        node);
    _enqueuedRechecks.insert(EnqueuedBucketRecheck(node, bucket));
}

void
BucketDBUpdater::sendAllQueuedBucketRechecks()
{
    LOG(spam,
        "Sending %zu queued bucket rechecks previously received "
        "via NotifyBucketChange commands",
        _enqueuedRechecks.size());

    typedef std::set<EnqueuedBucketRecheck>::const_iterator const_iterator;
    for (const_iterator it(_enqueuedRechecks.begin()),
             e(_enqueuedRechecks.end()); it != e; ++it)
    {
        sendRequestBucketInfo(it->node,
                              it->bucket,
                              std::shared_ptr<MergeReplyGuard>());
    }
    _enqueuedRechecks.clear();
}

bool
BucketDBUpdater::onNotifyBucketChange(
        const std::shared_ptr<api::NotifyBucketChangeCommand>& cmd)
{
    // Immediately schedule reply to ensure it is sent.
    _sender.sendReply(std::shared_ptr<api::StorageReply>(
            new api::NotifyBucketChangeReply(*cmd)));

    if (!cmd->getBucketInfo().valid()) {
        LOG(error,
            "Received invalid bucket info for bucket %s from notify bucket "
            "change! Not updating bucket.",
            cmd->getBucketId().toString().c_str());
        return true;
    }
    LOG(debug,
        "Received notify bucket change from node %u for bucket %s with %s.",
        cmd->getSourceIndex(),
        cmd->getBucketId().toString().c_str(),
        cmd->getBucketInfo().toString().c_str());

    if (hasPendingClusterState()) {
        enqueueRecheckUntilPendingStateEnabled(cmd->getSourceIndex(),
                                               cmd->getBucketId());
    } else {
        sendRequestBucketInfo(cmd->getSourceIndex(),
                              cmd->getBucketId(),
                              std::shared_ptr<MergeReplyGuard>());
    }

    return true;
}

bool sort_pred(const BucketListMerger::BucketEntry& left,
               const BucketListMerger::BucketEntry& right)
{
    return left.first < right.first;
}

bool
BucketDBUpdater::onRequestBucketInfoReply(
        const std::shared_ptr<api::RequestBucketInfoReply> & repl)
{
    if (pendingClusterStateAccepted(repl)) {
        return true;
    }
    return processSingleBucketInfoReply(repl);
}

bool
BucketDBUpdater::pendingClusterStateAccepted(
        const std::shared_ptr<api::RequestBucketInfoReply> & repl)
{
    if (_pendingClusterState.get()
        && _pendingClusterState->onRequestBucketInfoReply(repl))
    {
        if (isPendingClusterStateCompleted()) {
            processCompletedPendingClusterState();
        }
        return true;
    }
    LOG(spam,
        "Reply %s was not accepted by pending cluster state",
        repl->toString().c_str());
    return false;
}

void
BucketDBUpdater::handleSingleBucketInfoFailure(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        const BucketRequest& req)
{
    LOG(debug, "Request bucket info failed towards node %d: error was %s",
        req.targetNode, repl->getResult().toString().c_str());

    if (req.bucket != document::BucketId(0)) {
        framework::MilliSecTime sendTime(_bucketSpaceComponent.getClock());
        sendTime += framework::MilliSecTime(100);
        _delayedRequests.push_back(std::make_pair(sendTime, req));
    }
}

void
BucketDBUpdater::resendDelayedMessages()
{
    if (_pendingClusterState.get()) {
        _pendingClusterState->resendDelayedMessages();
    }
    if (_delayedRequests.empty()) return; // Don't fetch time if not needed
    framework::MilliSecTime currentTime(_bucketSpaceComponent.getClock());
    while (!_delayedRequests.empty()
           && currentTime >= _delayedRequests.front().first)
    {
        BucketRequest& req(_delayedRequests.front().second);
        sendRequestBucketInfo(req.targetNode,
                              req.bucket,
                              std::shared_ptr<MergeReplyGuard>());
        _delayedRequests.pop_front();
    }
}

void
BucketDBUpdater::convertBucketInfoToBucketList(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        uint16_t targetNode,
        BucketListMerger::BucketList& newList)
{
    for (uint32_t i = 0; i < repl->getBucketInfo().size(); i++) {
        LOG(debug,
            "Received bucket information from node %u for bucket %s: %s",
            targetNode,
            repl->getBucketInfo()[i]._bucketId.toString().c_str(),
            repl->getBucketInfo()[i]._info.toString().c_str());

        newList.push_back(BucketListMerger::BucketEntry(
                repl->getBucketInfo()[i]._bucketId,
                repl->getBucketInfo()[i]._info));
    }
}

void
BucketDBUpdater::mergeBucketInfoWithDatabase(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        const BucketRequest& req)
{
    BucketListMerger::BucketList existing;
    BucketListMerger::BucketList newList;

    findRelatedBucketsInDatabase(req.targetNode, req.bucket, existing);
    convertBucketInfoToBucketList(repl, req.targetNode, newList);

    std::sort(existing.begin(), existing.end(), sort_pred);
    std::sort(newList.begin(), newList.end(), sort_pred);

    BucketListMerger merger(newList, existing, req.timestamp);
    updateDatabase(req.targetNode, merger);
}

bool
BucketDBUpdater::processSingleBucketInfoReply(
        const std::shared_ptr<api::RequestBucketInfoReply> & repl)
{
    std::map<uint64_t, BucketRequest>::iterator iter =
        _sentMessages.find(repl->getMsgId());

    // Has probably been deleted for some reason earlier.
    if (iter == _sentMessages.end()) {
        return true;
    }

    BucketRequest req = iter->second;
    _sentMessages.erase(iter);

    if (!_bucketSpaceComponent.storageNodeIsUp(req.targetNode)) {
        // Ignore replies from nodes that are down.
        return true;
    }
    if (repl->getResult().getResult() != api::ReturnCode::OK) {
        handleSingleBucketInfoFailure(repl, req);
        return true;
    }
    mergeBucketInfoWithDatabase(repl, req);
    return true;
}

void
BucketDBUpdater::addBucketInfoForNode(
        const BucketDatabase::Entry& e,
        uint16_t node,
        BucketListMerger::BucketList& existing) const
{
    const BucketCopy* copy(e->getNode(node));
    if (copy) {
        existing.push_back(BucketListMerger::BucketEntry(
                e.getBucketId(), copy->getBucketInfo()));
    }
}

void
BucketDBUpdater::findRelatedBucketsInDatabase(
        uint16_t node,
        const document::BucketId& bucketId,
        BucketListMerger::BucketList& existing)
{
    std::vector<BucketDatabase::Entry> entries;
    _bucketSpaceComponent.getBucketDatabase().getAll(bucketId, entries);

    for (uint32_t j = 0; j < entries.size(); ++j) {
        addBucketInfoForNode(entries[j], node, existing);
    }
}

void
BucketDBUpdater::updateDatabase(uint16_t node, BucketListMerger& merger)
{
    for (uint32_t i = 0; i < merger.getRemovedEntries().size(); i++) {
        _bucketSpaceComponent.removeNodeFromDB(merger.getRemovedEntries()[i], node);
    }

    for (uint32_t i = 0; i < merger.getAddedEntries().size(); i++) {
        const BucketListMerger::BucketEntry& entry(
                merger.getAddedEntries()[i]);

        _bucketSpaceComponent.updateBucketDatabase(
                entry.first,
                BucketCopy(merger.getTimestamp(), node, entry.second),
                DatabaseUpdate::CREATE_IF_NONEXISTING);
    }
}

bool
BucketDBUpdater::isPendingClusterStateCompleted() const
{
    return _pendingClusterState.get() && _pendingClusterState->done();
}

void
BucketDBUpdater::processCompletedPendingClusterState()
{
    _pendingClusterState->mergeInto(_bucketSpaceComponent.getBucketDatabase());

    if (_pendingClusterState->getCommand().get()) {
        enableCurrentClusterStateInDistributor();
        _bucketSpaceComponent.getDistributor().getMessageSender().sendDown(
                _pendingClusterState->getCommand());
        addCurrentStateToClusterStateHistory();
    } else {
        _bucketSpaceComponent.getDistributor().notifyDistributionChangeEnabled();
    }

    _pendingClusterState.reset();
    _outdatedNodes.clear();
    sendAllQueuedBucketRechecks();
    completeTransitionTimer();
}

void
BucketDBUpdater::enableCurrentClusterStateInDistributor()
{
    const lib::ClusterState& state(
            _pendingClusterState->getCommand()->getSystemState());

    LOG(debug,
        "BucketDBUpdater finished processing state %s",
        state.toString().c_str());

    _bucketSpaceComponent.getDistributor().enableClusterState(state);
}

void
BucketDBUpdater::addCurrentStateToClusterStateHistory()
{
    _history.push_back(_pendingClusterState->getSummary());

    if (_history.size() > 50) {
        _history.pop_front();
    }
}

vespalib::string
BucketDBUpdater::getReportContentType(const framework::HttpUrlPath&) const
{
    return "text/xml";
}

namespace {

const vespalib::string ALL = "all";
const vespalib::string BUCKETDB = "bucketdb";
const vespalib::string BUCKETDB_UPDATER = "Bucket Database Updater";

}

bool
BucketDBUpdater::reportStatus(std::ostream& out,
                              const framework::HttpUrlPath& path) const
{
    using namespace vespalib::xml;
    XmlOutputStream xos(out);
    // FIXME(vekterli): have to do this manually since we cannot inherit
    // directly from XmlStatusReporter due to data races when BucketDBUpdater
    // gets status requests directly.
    xos << XmlTag("status")
        << XmlAttribute("id", BUCKETDB)
        << XmlAttribute("name", BUCKETDB_UPDATER);
    reportXmlStatus(xos, path);
    xos << XmlEndTag();
    return true;
}

vespalib::string
BucketDBUpdater::reportXmlStatus(vespalib::xml::XmlOutputStream& xos,
                                 const framework::HttpUrlPath&) const
{
    using namespace vespalib::xml;
    xos << XmlTag("bucketdb")
        << XmlTag("systemstate_active")
        << XmlContent(_bucketSpaceComponent.getClusterState().toString())
        << XmlEndTag();
    if (_pendingClusterState.get() != 0) {
        xos << *_pendingClusterState;
    }
    xos << XmlTag("systemstate_history");
    typedef std::list<PendingClusterState::Summary>::const_reverse_iterator HistoryIter;
    for (HistoryIter i(_history.rbegin()), e(_history.rend()); i != e; ++i) {
        xos << XmlTag("change")
            << XmlAttribute("from", i->_prevClusterState)
            << XmlAttribute("to", i->_newClusterState)
            << XmlAttribute("processingtime", i->_processingTime)
            << XmlEndTag();
    }
    xos << XmlEndTag()
        << XmlTag("single_bucket_requests");
    for (std::map<uint64_t, BucketRequest>::const_iterator iter
            = _sentMessages.begin(); iter != _sentMessages.end(); iter++)
    {
        xos << XmlTag("storagenode")
            << XmlAttribute("index", iter->second.targetNode);
        if (iter->second.bucket.getRawId() == 0) {
            xos << XmlAttribute("bucket", ALL);
        } else {
            xos << XmlAttribute("bucket", iter->second.bucket.getId(),
                                XmlAttribute::HEX);
        }
        xos << XmlAttribute("sendtimestamp", iter->second.timestamp)
            << XmlEndTag();
    }
    xos << XmlEndTag() << XmlEndTag();
    return "";
}

bool
BucketDBUpdater::BucketListGenerator::process(BucketDatabase::Entry& e)
{
    document::BucketId bucketId(e.getBucketId());

    const BucketCopy* copy(e->getNode(_node));
    if (copy) {
        _entries.push_back(
                BucketListMerger::BucketEntry(
                        bucketId,
                        copy->getBucketInfo()));
    }
    return true;
}

void
BucketDBUpdater::NodeRemover::logRemove(const document::BucketId& bucketId,
                                        const char* msg) const
{
    LOG(spam, "Removing bucket %s: %s", bucketId.toString().c_str(), msg);
    LOG_BUCKET_OPERATION_NO_LOCK(bucketId, msg);    
}

bool
BucketDBUpdater::NodeRemover::distributorOwnsBucket(
        const document::BucketId& bucketId) const
{
    try {
        uint16_t distributor(
                _distribution.getIdealDistributorNode(_state, bucketId, "uim"));
        if (distributor != _localIndex) {
            logRemove(bucketId, "bucket now owned by another distributor");
            return false;
        }
        return true;
    } catch (lib::TooFewBucketBitsInUseException& exc) {
        logRemove(bucketId, "using too few distribution bits now");
    } catch (lib::NoDistributorsAvailableException& exc) {
        logRemove(bucketId, "no distributors are available");
    }
    return false;
}

void
BucketDBUpdater::NodeRemover::setCopiesInEntry(
        BucketDatabase::Entry& e,
        const std::vector<BucketCopy>& copies) const
{
    e->clear();

    std::vector<uint16_t> order =
            _distribution.getIdealStorageNodes(_state, e.getBucketId(), _upStates);

    e->addNodes(copies, order);

    LOG(debug, "Changed %s", e->toString().c_str());
    LOG_BUCKET_OPERATION_NO_LOCK(
            e.getBucketId(),
            vespalib::make_vespa_string("updated bucketdb entry to %s",
                                        e->toString().c_str()));
}

void
BucketDBUpdater::NodeRemover::removeEmptyBucket(const document::BucketId& bucketId)
{
    _removedBuckets.push_back(bucketId);

    LOG(debug,
        "After system state change %s, bucket %s now has no copies.",
        _oldState.getTextualDifference(_state).c_str(),
        bucketId.toString().c_str());
    LOG_BUCKET_OPERATION_NO_LOCK(bucketId, "bucket now has no copies");
}

bool
BucketDBUpdater::NodeRemover::process(BucketDatabase::Entry& e)
{
    const document::BucketId& bucketId(e.getBucketId());

    LOG(spam, "Check for remove: bucket %s", e.toString().c_str());
    if (e->getNodeCount() == 0) {
        removeEmptyBucket(e.getBucketId());
        return true;
    }
    if (!distributorOwnsBucket(bucketId)) {
        _removedBuckets.push_back(bucketId);
        return true;
    }

    std::vector<BucketCopy> remainingCopies;
    for (uint16_t i = 0; i < e->getNodeCount(); i++) {
        Node n(NodeType::STORAGE, e->getNodeRef(i).getNode());

        if (_state.getNodeState(n).getState().oneOf(_upStates)) {
            remainingCopies.push_back(e->getNodeRef(i));
        }
    }

    if (remainingCopies.size() == e->getNodeCount()) {
        return true;
    }

    if (remainingCopies.empty()) {
        removeEmptyBucket(bucketId);
    } else {
        setCopiesInEntry(e, remainingCopies);
    }

    return true;
}

BucketDBUpdater::NodeRemover::~NodeRemover()
{
    if (_removedBuckets.size() > 0) {
        std::ostringstream ost;
        ost << "After system state change "
            << _oldState.getTextualDifference(_state) << ", we removed "
            << "buckets. Data is unavailable until node comes back up. "
            << _removedBuckets.size() << " buckets removed:";
        for (uint32_t i=0; i < 10 && i < _removedBuckets.size(); ++i) {
            ost << " " << _removedBuckets[i];
        }
        if (_removedBuckets.size() >= 10) {
            ost << " ...";
        }
        LOGBM(info, ost.str().c_str());
    }
}

} // distributor
