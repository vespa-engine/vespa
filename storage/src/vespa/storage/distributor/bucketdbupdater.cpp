// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdbupdater.h"
#include "bucket_db_prune_elision.h"
#include "bucket_space_distribution_context.h"
#include "distributor.h"
#include "distributor_bucket_space.h"
#include "distributormetricsset.h"
#include "simpleclusterinformation.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <thread>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".distributor.bucketdb.updater");

using storage::lib::Node;
using storage::lib::NodeType;
using document::BucketSpace;

namespace storage::distributor {

BucketDBUpdater::BucketDBUpdater(Distributor& owner,
                                 DistributorBucketSpaceRepo& bucketSpaceRepo,
                                 DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
                                 DistributorMessageSender& sender,
                                 DistributorComponentRegister& compReg)
    : framework::StatusReporter("bucketdb", "Bucket DB Updater"),
      _distributorComponent(owner, bucketSpaceRepo, readOnlyBucketSpaceRepo, compReg, "Bucket DB Updater"),
      _sender(sender),
      _transitionTimer(_distributorComponent.getClock()),
      _stale_reads_enabled(false),
      _active_distribution_contexts(),
      _explicit_transition_read_guard(),
      _distribution_context_mutex()
{
    for (auto& elem : _distributorComponent.getBucketSpaceRepo()) {
        _active_distribution_contexts.emplace(
                elem.first,
                BucketSpaceDistributionContext::make_not_yet_initialized(_distributorComponent.getIndex()));
        _explicit_transition_read_guard.emplace(elem.first, std::shared_ptr<BucketDatabase::ReadGuard>());
    }
}

BucketDBUpdater::~BucketDBUpdater() = default;

OperationRoutingSnapshot BucketDBUpdater::read_snapshot_for_bucket(const document::Bucket& bucket) const {
    const auto bucket_space = bucket.getBucketSpace();
    std::lock_guard lock(_distribution_context_mutex);
    auto active_state_iter = _active_distribution_contexts.find(bucket_space);
    assert(active_state_iter != _active_distribution_contexts.cend());
    auto& state = *active_state_iter->second;
    if (!state.bucket_owned_in_active_state(bucket.getBucketId())) {
        return OperationRoutingSnapshot::make_not_routable_in_state(active_state_iter->second);
    }
    const bool bucket_present_in_mutable_db = state.bucket_owned_in_pending_state(bucket.getBucketId());
    if (!bucket_present_in_mutable_db && !stale_reads_enabled()) {
        return OperationRoutingSnapshot::make_not_routable_in_state(active_state_iter->second);
    }
    const auto& space_repo = bucket_present_in_mutable_db
            ? _distributorComponent.getBucketSpaceRepo()
            : _distributorComponent.getReadOnlyBucketSpaceRepo();
    auto existing_guard_iter = _explicit_transition_read_guard.find(bucket_space);
    assert(existing_guard_iter != _explicit_transition_read_guard.cend());
    auto db_guard = existing_guard_iter->second
            ? existing_guard_iter-> second
            : space_repo.get(bucket_space).getBucketDatabase().acquire_read_guard();
    return OperationRoutingSnapshot::make_routable_with_guard(active_state_iter->second, std::move(db_guard), space_repo);
}

void
BucketDBUpdater::flush()
{
    for (auto & entry : _sentMessages) {
        // Cannot sendDown MergeBucketReplies during flushing, since
        // all lower links have been closed
        if (entry.second._mergeReplyGuard) {
            entry.second._mergeReplyGuard->resetReply();
        }
    }
    _sentMessages.clear();
}

void
BucketDBUpdater::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "BucketDBUpdater";
}

bool
BucketDBUpdater::shouldDeferStateEnabling() const noexcept
{
    return stale_reads_enabled();
}

bool
BucketDBUpdater::hasPendingClusterState() const
{
    return static_cast<bool>(_pendingClusterState);
}

const lib::ClusterState*
BucketDBUpdater::pendingClusterStateOrNull(const document::BucketSpace& space) const {
    return (hasPendingClusterState()
            ? _pendingClusterState->getNewClusterStateBundle().getDerivedClusterState(space).get()
            : nullptr);
}

void
BucketDBUpdater::sendRequestBucketInfo(
        uint16_t node,
        const document::Bucket& bucket,
        const std::shared_ptr<MergeReplyGuard>& mergeReplyGuard)
{
    if (!_distributorComponent.storageNodeIsUp(bucket.getBucketSpace(), node)) {
        return;
    }

    std::vector<document::BucketId> buckets;
    buckets.push_back(bucket.getBucketId());

    std::shared_ptr<api::RequestBucketInfoCommand> msg(
            new api::RequestBucketInfoCommand(bucket.getBucketSpace(), buckets));

    LOG(debug,
        "Sending request bucket info command %" PRIu64 " for "
        "bucket %s to node %u",
        msg->getMsgId(),
        bucket.toString().c_str(),
        node);

    msg->setPriority(50);
    msg->setAddress(_distributorComponent.nodeAddress(node));

    _sentMessages[msg->getMsgId()] =
        BucketRequest(node, _distributorComponent.getUniqueTimestamp(),
                      bucket, mergeReplyGuard);
    _sender.sendCommand(msg);
}

void
BucketDBUpdater::recheckBucketInfo(uint32_t nodeIdx,
                                   const document::Bucket& bucket)
{
    sendRequestBucketInfo(nodeIdx, bucket, std::shared_ptr<MergeReplyGuard>());
}

namespace {

class ReadOnlyDbMergingInserter : public BucketDatabase::MergingProcessor {
    using NewEntries = std::vector<BucketDatabase::Entry>;
    NewEntries::const_iterator _current;
    const NewEntries::const_iterator _last;
public:
    explicit ReadOnlyDbMergingInserter(const NewEntries& new_entries)
        : _current(new_entries.cbegin()),
          _last(new_entries.cend())
    {}

    Result merge(BucketDatabase::Merger& m) override {
        const uint64_t key_to_insert = m.bucket_key();
        uint64_t key_at_cursor = 0;
        while (_current != _last) {
            key_at_cursor = _current->getBucketId().toKey();
            if (key_at_cursor >= key_to_insert) {
                break;
            }
            m.insert_before_current(_current->getBucketId(), *_current);
            ++_current;
        }
        if ((_current != _last) && (key_at_cursor == key_to_insert)) {
            // If we encounter a bucket that already exists, replace value wholesale.
            // Don't try to cleverly merge replicas, as the values we currently hold
            // in the read-only DB may be stale.
            // Note that this case shouldn't really happen, since we only add previously
            // owned buckets to the read-only DB, and subsequent adds to a non-empty DB
            // can only happen for state preemptions. Since ownership is not regained
            // before a state is stable, a bucket is only added once. But we handle it
            // anyway in case this changes at some point in the future.
            m.current_entry() = *_current;
            return Result::Update;
        }
        return Result::KeepUnchanged;
    }

    void insert_remaining_at_end(BucketDatabase::TrailingInserter& inserter) override {
        for (; _current != _last; ++_current) {
            inserter.insert_at_end(_current->getBucketId(), *_current);
        }
    }
};

}

void
BucketDBUpdater::removeSuperfluousBuckets(
        const lib::ClusterStateBundle& newState,
        bool is_distribution_config_change)
{
    const bool move_to_read_only_db = shouldDeferStateEnabling();
    const char* up_states = _distributorComponent.getDistributor().getStorageNodeUpStates();
    for (auto& elem : _distributorComponent.getBucketSpaceRepo()) {
        const auto& newDistribution(elem.second->getDistribution());
        const auto& oldClusterState(elem.second->getClusterState());
        const auto& new_cluster_state = newState.getDerivedClusterState(elem.first);

        // Running a full DB sweep is expensive, so if the cluster state transition does
        // not actually indicate that buckets should possibly be removed, we elide it entirely.
        if (!is_distribution_config_change
            && db_pruning_may_be_elided(oldClusterState, *new_cluster_state, up_states))
        {
            LOG(debug, "[bucket space '%s']: eliding DB pruning for state transition '%s' -> '%s'",
                document::FixedBucketSpaces::to_string(elem.first).data(),
                oldClusterState.toString().c_str(), new_cluster_state->toString().c_str());
            continue;
        }

        auto& bucketDb(elem.second->getBucketDatabase());
        auto& readOnlyDb(_distributorComponent.getReadOnlyBucketSpaceRepo().get(elem.first).getBucketDatabase());

        // Remove all buckets not belonging to this distributor, or
        // being on storage nodes that are no longer up.
        MergingNodeRemover proc(
                oldClusterState,
                *new_cluster_state,
                _distributorComponent.getIndex(),
                newDistribution,
                up_states,
                move_to_read_only_db);

        bucketDb.merge(proc);
        if (move_to_read_only_db) {
            ReadOnlyDbMergingInserter read_only_merger(proc.getNonOwnedEntries());
            readOnlyDb.merge(read_only_merger);
        }
        maybe_inject_simulated_db_pruning_delay();
    }
}

namespace {

void maybe_sleep_for(std::chrono::milliseconds ms) {
    if (ms.count() > 0) {
        std::this_thread::sleep_for(ms);
    }
}

}

void
BucketDBUpdater::maybe_inject_simulated_db_pruning_delay() {
    maybe_sleep_for(_distributorComponent.getDistributor().getConfig().simulated_db_pruning_latency());
}

void
BucketDBUpdater::maybe_inject_simulated_db_merging_delay() {
    maybe_sleep_for(_distributorComponent.getDistributor().getConfig().simulated_db_merging_latency());
}

void
BucketDBUpdater::ensureTransitionTimerStarted()
{
    // Don't overwrite start time if we're already processing a state, as
    // that will make transition times appear artificially low.
    if (!hasPendingClusterState()) {
        _transitionTimer = framework::MilliSecTimer(
                _distributorComponent.getClock());
    }
}

void
BucketDBUpdater::completeTransitionTimer()
{
    _distributorComponent.getDistributor().getMetrics()
            .stateTransitionTime.addValue(_transitionTimer.getElapsedTimeAsDouble());
}

void
BucketDBUpdater::clearReadOnlyBucketRepoDatabases()
{
    for (auto& space : _distributorComponent.getReadOnlyBucketSpaceRepo()) {
        space.second->getBucketDatabase().clear();
    }
}

void
BucketDBUpdater::storageDistributionChanged()
{
    ensureTransitionTimerStarted();

    removeSuperfluousBuckets(_distributorComponent.getClusterStateBundle(), true);

    ClusterInformation::CSP clusterInfo(new SimpleClusterInformation(
            _distributorComponent.getIndex(),
            _distributorComponent.getClusterStateBundle(),
            _distributorComponent.getDistributor().getStorageNodeUpStates()));
    _pendingClusterState = PendingClusterState::createForDistributionChange(
            _distributorComponent.getClock(),
            std::move(clusterInfo),
            _sender,
            _distributorComponent.getBucketSpaceRepo(),
            _distributorComponent.getUniqueTimestamp());
    _outdatedNodesMap = _pendingClusterState->getOutdatedNodesMap();
    _distributorComponent.getBucketSpaceRepo().set_pending_cluster_state_bundle(_pendingClusterState->getNewClusterStateBundle());
}

void
BucketDBUpdater::replyToPreviousPendingClusterStateIfAny()
{
    if (_pendingClusterState.get() && _pendingClusterState->hasCommand()) {
        _distributorComponent.sendUp(
                std::make_shared<api::SetSystemStateReply>(*_pendingClusterState->getCommand()));
    }
}

void
BucketDBUpdater::replyToActivationWithActualVersion(
        const api::ActivateClusterStateVersionCommand& cmd,
        uint32_t actualVersion)
{
    auto reply = std::make_shared<api::ActivateClusterStateVersionReply>(cmd);
    reply->setActualVersion(actualVersion);
    _distributorComponent.sendUp(reply); // TODO let API accept rvalues
}

void BucketDBUpdater::update_read_snapshot_before_db_pruning() {
    std::lock_guard lock(_distribution_context_mutex);
    for (auto& elem : _distributorComponent.getBucketSpaceRepo()) {
        // At this point, we're still operating with a distribution context _without_ a
        // pending state, i.e. anyone using the context will expect to find buckets
        // in the DB that correspond to how the database looked like prior to pruning
        // buckets from the DB. To ensure this is not violated, take a snapshot of the
        // _mutable_ DB and expose this. This snapshot only lives until we atomically
        // flip to expose a distribution context that includes the new, pending state.
        // At that point, the read-only DB is known to contain the buckets that have
        // been pruned away, so we can release the mutable DB snapshot safely.
        // TODO test for, and handle, state preemption case!
        _explicit_transition_read_guard[elem.first] = elem.second->getBucketDatabase().acquire_read_guard();
    }
}


void BucketDBUpdater::update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) {
    std::lock_guard lock(_distribution_context_mutex);
    const auto old_default_state = _distributorComponent.getBucketSpaceRepo().get(
            document::FixedBucketSpaces::default_space()).cluster_state_sp();
    for (auto& elem : _distributorComponent.getBucketSpaceRepo()) {
        auto new_distribution  = elem.second->distribution_sp();
        auto old_cluster_state = elem.second->cluster_state_sp();
        auto new_cluster_state = new_state.getDerivedClusterState(elem.first);
        _active_distribution_contexts.insert_or_assign(
                elem.first,
                BucketSpaceDistributionContext::make_state_transition(
                        std::move(old_cluster_state),
                        old_default_state,
                        std::move(new_cluster_state),
                        std::move(new_distribution),
                        _distributorComponent.getIndex()));
        // We can now remove the explicit mutable DB snapshot, as the buckets that have been
        // pruned away are visible in the read-only DB.
        _explicit_transition_read_guard[elem.first] = std::shared_ptr<BucketDatabase::ReadGuard>();
    }
}

void BucketDBUpdater::update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) {
    std::lock_guard lock(_distribution_context_mutex);
    const auto& default_cluster_state = activated_state.getDerivedClusterState(document::FixedBucketSpaces::default_space());
    for (auto& elem : _distributorComponent.getBucketSpaceRepo()) {
        auto new_distribution  = elem.second->distribution_sp();
        auto new_cluster_state = activated_state.getDerivedClusterState(elem.first);
        _active_distribution_contexts.insert_or_assign(
                elem.first,
                BucketSpaceDistributionContext::make_stable_state(
                        std::move(new_cluster_state),
                        default_cluster_state,
                        std::move(new_distribution),
                        _distributorComponent.getIndex()));
    }
}

bool
BucketDBUpdater::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    LOG(debug,
        "Received new cluster state %s",
        cmd->getSystemState().toString().c_str());

    const lib::ClusterStateBundle oldState = _distributorComponent.getClusterStateBundle();
    const lib::ClusterStateBundle& state = cmd->getClusterStateBundle();

    if (state == oldState) {
        return false;
    }
    ensureTransitionTimerStarted();
    // Separate timer since _transitionTimer might span multiple pending states.
    framework::MilliSecTimer process_timer(_distributorComponent.getClock());
    update_read_snapshot_before_db_pruning();
    const auto& bundle = cmd->getClusterStateBundle();
    removeSuperfluousBuckets(bundle, false);
    update_read_snapshot_after_db_pruning(bundle);
    replyToPreviousPendingClusterStateIfAny();

    ClusterInformation::CSP clusterInfo(
            new SimpleClusterInformation(
                _distributorComponent.getIndex(),
                _distributorComponent.getClusterStateBundle(),
                _distributorComponent.getDistributor()
                .getStorageNodeUpStates()));
    _pendingClusterState = PendingClusterState::createForClusterStateChange(
            _distributorComponent.getClock(),
            std::move(clusterInfo),
            _sender,
            _distributorComponent.getBucketSpaceRepo(),
            cmd,
            _outdatedNodesMap,
            _distributorComponent.getUniqueTimestamp());
    _outdatedNodesMap = _pendingClusterState->getOutdatedNodesMap();

    _distributorComponent.getDistributor().getMetrics().set_cluster_state_processing_time.addValue(
            process_timer.getElapsedTimeAsDouble());

    _distributorComponent.getBucketSpaceRepo().set_pending_cluster_state_bundle(_pendingClusterState->getNewClusterStateBundle());
    if (isPendingClusterStateCompleted()) {
        processCompletedPendingClusterState();
    }
    return true;
}

bool
BucketDBUpdater::onActivateClusterStateVersion(const std::shared_ptr<api::ActivateClusterStateVersionCommand>& cmd)
{
    if (hasPendingClusterState() && _pendingClusterState->isVersionedTransition()) {
        const auto pending_version = _pendingClusterState->clusterStateVersion();
        if (pending_version == cmd->version()) {
            if (isPendingClusterStateCompleted()) {
                assert(_pendingClusterState->isDeferred());
                activatePendingClusterState();
            } else {
                LOG(error, "Received cluster state activation for pending version %u "
                           "without pending state being complete yet. This is not expected, "
                           "as no activation should be sent before all distributors have "
                           "reported that state processing is complete.", pending_version);
                replyToActivationWithActualVersion(*cmd, 0);  // Invalid version, will cause re-send (hopefully when completed).
                return true;
            }
        } else {
            replyToActivationWithActualVersion(*cmd, pending_version);
            return true;
        }
    } else if (shouldDeferStateEnabling()) {
        // Likely just a resend, but log warn for now to get a feel of how common it is.
        LOG(warning, "Received cluster state activation command for version %u, which "
                     "has no corresponding pending state. Likely resent operation.", cmd->version());
    } else {
        LOG(debug, "Received cluster state activation command for version %u, but distributor "
                   "config does not have deferred activation enabled. Treating as no-op.", cmd->version());
    }
    // Fall through to next link in call chain that cares about this message.
    return false;
}

BucketDBUpdater::MergeReplyGuard::~MergeReplyGuard()
{
    if (_reply) {
        _updater.getDistributorComponent().getDistributor().handleCompletedMerge(_reply);
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
                             reply->getBucket(),
                             replyGuard);
   }

   return true;
}

void
BucketDBUpdater::enqueueRecheckUntilPendingStateEnabled(
        uint16_t node,
        const document::Bucket& bucket)
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

    for (const auto & entry :_enqueuedRechecks) {
        sendRequestBucketInfo(entry.node, entry.bucket, std::shared_ptr<MergeReplyGuard>());
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
                                               cmd->getBucket());
    } else {
        sendRequestBucketInfo(cmd->getSourceIndex(),
                              cmd->getBucket(),
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

    if (req.bucket.getBucketId() != document::BucketId(0)) {
        framework::MilliSecTime sendTime(_distributorComponent.getClock());
        sendTime += framework::MilliSecTime(100);
        _delayedRequests.emplace_back(sendTime, req);
    }
}

void
BucketDBUpdater::resendDelayedMessages()
{
    if (_pendingClusterState) {
        _pendingClusterState->resendDelayedMessages();
    }
    if (_delayedRequests.empty()) return; // Don't fetch time if not needed
    framework::MilliSecTime currentTime(_distributorComponent.getClock());
    while (!_delayedRequests.empty()
           && currentTime >= _delayedRequests.front().first)
    {
        BucketRequest& req(_delayedRequests.front().second);
        sendRequestBucketInfo(req.targetNode, req.bucket, std::shared_ptr<MergeReplyGuard>());
        _delayedRequests.pop_front();
    }
}

void
BucketDBUpdater::convertBucketInfoToBucketList(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        uint16_t targetNode, BucketListMerger::BucketList& newList)
{
    for (const auto & entry : repl->getBucketInfo()) {
        LOG(debug, "Received bucket information from node %u for bucket %s: %s", targetNode,
            entry._bucketId.toString().c_str(), entry._info.toString().c_str());

        newList.emplace_back(entry._bucketId, entry._info);
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
    updateDatabase(req.bucket.getBucketSpace(), req.targetNode, merger);
}

bool
BucketDBUpdater::processSingleBucketInfoReply(
        const std::shared_ptr<api::RequestBucketInfoReply> & repl)
{
    auto iter = _sentMessages.find(repl->getMsgId());

    // Has probably been deleted for some reason earlier.
    if (iter == _sentMessages.end()) {
        return true;
    }

    BucketRequest req = iter->second;
    _sentMessages.erase(iter);

    if (!_distributorComponent.storageNodeIsUp(req.bucket.getBucketSpace(), req.targetNode)) {
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
        existing.emplace_back(e.getBucketId(), copy->getBucketInfo());
    }
}

void
BucketDBUpdater::findRelatedBucketsInDatabase(uint16_t node, const document::Bucket& bucket,
                                              BucketListMerger::BucketList& existing)
{
    auto &distributorBucketSpace(_distributorComponent.getBucketSpaceRepo().get(bucket.getBucketSpace()));
    std::vector<BucketDatabase::Entry> entries;
    distributorBucketSpace.getBucketDatabase().getAll(bucket.getBucketId(), entries);

    for (const BucketDatabase::Entry & entry : entries) {
        addBucketInfoForNode(entry, node, existing);
    }
}

void
BucketDBUpdater::updateDatabase(document::BucketSpace bucketSpace, uint16_t node, BucketListMerger& merger)
{
    for (const document::BucketId & bucketId : merger.getRemovedEntries()) {
        document::Bucket bucket(bucketSpace, bucketId);
        _distributorComponent.removeNodeFromDB(bucket, node);
    }

    for (const BucketListMerger::BucketEntry& entry : merger.getAddedEntries()) {
        document::Bucket bucket(bucketSpace, entry.first);
        _distributorComponent.updateBucketDatabase(
                bucket,
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
    if (_pendingClusterState->isDeferred()) {
        LOG(debug, "Deferring completion of pending cluster state version %u until explicitly activated",
                   _pendingClusterState->clusterStateVersion());
        assert(_pendingClusterState->hasCommand()); // Deferred transitions should only ever be created by state commands.
        // Sending down SetSystemState command will reach the state manager and a reply
        // will be auto-sent back to the cluster controller in charge. Once this happens,
        // it will send an explicit activation command once all distributors have reported
        // that their pending cluster states have completed.
        // A booting distributor will treat itself as "system Up" before the state has actually
        // taken effect via activation. External operation handler will keep operations from
        // actually being scheduled until state has been activated. The external operation handler
        // needs to be explicitly aware of the case where no state has yet to be activated.
        _distributorComponent.getDistributor().getMessageSender().sendDown(
                _pendingClusterState->getCommand());
        _pendingClusterState->clearCommand();
        return;
    }
    // Distribution config change or non-deferred cluster state. Immediately activate
    // the pending state without being told to do so explicitly.
    activatePendingClusterState();
}

void
BucketDBUpdater::activatePendingClusterState()
{
    framework::MilliSecTimer process_timer(_distributorComponent.getClock());

    _pendingClusterState->mergeIntoBucketDatabases();
    maybe_inject_simulated_db_merging_delay();

    if (_pendingClusterState->isVersionedTransition()) {
        LOG(debug, "Activating pending cluster state version %u", _pendingClusterState->clusterStateVersion());
        enableCurrentClusterStateBundleInDistributor();
        if (_pendingClusterState->hasCommand()) {
            _distributorComponent.getDistributor().getMessageSender().sendDown(
                    _pendingClusterState->getCommand());
        }
        addCurrentStateToClusterStateHistory();
    } else {
        LOG(debug, "Activating pending distribution config");
        // TODO distribution changes cannot currently be deferred as they are not
        // initiated by the cluster controller!
        _distributorComponent.getDistributor().notifyDistributionChangeEnabled();
    }

    update_read_snapshot_after_activation(_pendingClusterState->getNewClusterStateBundle());
    _pendingClusterState.reset();
    _outdatedNodesMap.clear();
    _distributorComponent.getBucketSpaceRepo().clear_pending_cluster_state_bundle();
    sendAllQueuedBucketRechecks();
    completeTransitionTimer();
    clearReadOnlyBucketRepoDatabases();

    _distributorComponent.getDistributor().getMetrics().activate_cluster_state_processing_time.addValue(
            process_timer.getElapsedTimeAsDouble());
}

void
BucketDBUpdater::enableCurrentClusterStateBundleInDistributor()
{
    const lib::ClusterStateBundle& state(
            _pendingClusterState->getNewClusterStateBundle());

    LOG(debug,
        "BucketDBUpdater finished processing state %s",
        state.getBaselineClusterState()->toString().c_str());

    _distributorComponent.getDistributor().enableClusterStateBundle(state);
}

void BucketDBUpdater::simulate_cluster_state_bundle_activation(const lib::ClusterStateBundle& activated_state) {
    update_read_snapshot_after_activation(activated_state);
    _distributorComponent.getDistributor().enableClusterStateBundle(activated_state);
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

void
BucketDBUpdater::BucketRequest::print_xml_tag(vespalib::xml::XmlOutputStream &xos, const vespalib::xml::XmlAttribute &timestampAttribute) const
{
    using namespace vespalib::xml;
    xos << XmlTag("storagenode")
        << XmlAttribute("index", targetNode);
    xos << XmlAttribute("bucketspace", bucket.getBucketSpace().getId(), XmlAttribute::HEX);
    if (bucket.getBucketId().getRawId() == 0) {
        xos << XmlAttribute("bucket", ALL);
    } else {
        xos << XmlAttribute("bucket", bucket.getBucketId().getId(), XmlAttribute::HEX);
    }
    xos << timestampAttribute << XmlEndTag();
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
        << XmlContent(_distributorComponent.getClusterStateBundle().getBaselineClusterState()->toString())
        << XmlEndTag();
    if (_pendingClusterState) {
        xos << *_pendingClusterState;
    }
    xos << XmlTag("systemstate_history");
    for (auto i(_history.rbegin()), e(_history.rend()); i != e; ++i) {
        xos << XmlTag("change")
            << XmlAttribute("from", i->_prevClusterState)
            << XmlAttribute("to", i->_newClusterState)
            << XmlAttribute("processingtime", i->_processingTime)
            << XmlEndTag();
    }
    xos << XmlEndTag()
        << XmlTag("single_bucket_requests");
    for (const auto & entry : _sentMessages)
    {
        entry.second.print_xml_tag(xos, XmlAttribute("sendtimestamp", entry.second.timestamp));
    }
    xos << XmlEndTag()
        << XmlTag("delayed_single_bucket_requests");
    for (const auto & entry : _delayedRequests)
    {
        entry.second.print_xml_tag(xos, XmlAttribute("resendtimestamp", entry.first.getTime()));
    }
    xos << XmlEndTag() << XmlEndTag();
    return "";
}

BucketDBUpdater::MergingNodeRemover::MergingNodeRemover(
        const lib::ClusterState& oldState,
        const lib::ClusterState& s,
        uint16_t localIndex,
        const lib::Distribution& distribution,
        const char* upStates,
        bool track_non_owned_entries)
    : _oldState(oldState),
      _state(s),
      _available_nodes(),
      _nonOwnedBuckets(),
      _removed_buckets(0),
      _removed_documents(0),
      _localIndex(localIndex),
      _distribution(distribution),
      _upStates(upStates),
      _track_non_owned_entries(track_non_owned_entries),
      _cachedDecisionSuperbucket(UINT64_MAX),
      _cachedOwned(false)
{
    // TODO intersection of cluster state and distribution config
    const uint16_t storage_count = s.getNodeCount(lib::NodeType::STORAGE);
    _available_nodes.resize(storage_count);
    for (uint16_t i = 0; i < storage_count; ++i) {
        if (s.getNodeState(lib::Node(lib::NodeType::STORAGE, i)).getState().oneOf(_upStates)) {
            _available_nodes[i] = true;
        }
    }
}

void
BucketDBUpdater::MergingNodeRemover::logRemove(const document::BucketId& bucketId, const char* msg) const
{
    LOG(spam, "Removing bucket %s: %s", bucketId.toString().c_str(), msg);
}

namespace {

uint64_t superbucket_from_id(const document::BucketId& id, uint16_t distribution_bits) noexcept {
    // The n LSBs of the bucket ID contain the superbucket number. Mask off the rest.
    return id.getRawId() & ~(UINT64_MAX << distribution_bits);
}

}

bool
BucketDBUpdater::MergingNodeRemover::distributorOwnsBucket(
        const document::BucketId& bucketId) const
{
    // TODO "no distributors available" case is the same for _all_ buckets; cache once in constructor.
    // TODO "too few bits used" case can be cheaply checked without needing exception
    try {
        const auto bits = _state.getDistributionBitCount();
        const auto this_superbucket = superbucket_from_id(bucketId, bits);
        if (_cachedDecisionSuperbucket == this_superbucket) {
            if (!_cachedOwned) {
                logRemove(bucketId, "bucket now owned by another distributor (cached)");
            }
            return _cachedOwned;
        }

        uint16_t distributor = _distribution.getIdealDistributorNode(_state, bucketId, "uim");
        _cachedDecisionSuperbucket = this_superbucket;
        _cachedOwned = (distributor == _localIndex);
        if (!_cachedOwned) {
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
BucketDBUpdater::MergingNodeRemover::setCopiesInEntry(
        BucketDatabase::Entry& e,
        const std::vector<BucketCopy>& copies) const
{
    e->clear();

    std::vector<uint16_t> order =
            _distribution.getIdealStorageNodes(_state, e.getBucketId(), _upStates);

    e->addNodes(copies, order);

    LOG(spam, "Changed %s", e->toString().c_str());
}

bool
BucketDBUpdater::MergingNodeRemover::has_unavailable_nodes(const storage::BucketDatabase::Entry& e) const
{
    const uint16_t n_nodes = e->getNodeCount();
    for (uint16_t i = 0; i < n_nodes; i++) {
        const uint16_t node_idx = e->getNodeRef(i).getNode();
        if (!storage_node_is_available(node_idx)) {
            return true;
        }
    }
    return false;
}

BucketDatabase::MergingProcessor::Result
BucketDBUpdater::MergingNodeRemover::merge(storage::BucketDatabase::Merger& merger)
{
    document::BucketId bucketId(merger.bucket_id());
    LOG(spam, "Check for remove: bucket %s", bucketId.toString().c_str());
    if (!distributorOwnsBucket(bucketId)) {
        // TODO remove in favor of DB snapshotting
        if (_track_non_owned_entries) {
            _nonOwnedBuckets.emplace_back(merger.current_entry());
        }
        return Result::Skip;
    }
    auto& e = merger.current_entry();

    if (e->getNodeCount() == 0) { // TODO when should this edge ever trigger?
        return Result::Skip;
    }

    if (!has_unavailable_nodes(e)) {
        return Result::KeepUnchanged;
    }

    std::vector<BucketCopy> remainingCopies;
    for (uint16_t i = 0; i < e->getNodeCount(); i++) {
        const uint16_t node_idx = e->getNodeRef(i).getNode();
        if (storage_node_is_available(node_idx)) {
            remainingCopies.push_back(e->getNodeRef(i));
        }
    }

    if (remainingCopies.empty()) {
        ++_removed_buckets;
        _removed_documents += e->getHighestDocumentCount();
        return Result::Skip;
    } else {
        setCopiesInEntry(e, remainingCopies);
        return Result::Update;
    }
}

bool
BucketDBUpdater::MergingNodeRemover::storage_node_is_available(uint16_t index) const noexcept
{
    return ((index < _available_nodes.size()) && _available_nodes[index]);
}

BucketDBUpdater::MergingNodeRemover::~MergingNodeRemover()
{
    if (_removed_buckets != 0) {
        LOGBM(info, "After cluster state change %s, %zu buckets no longer "
                    "have available replicas. %zu documents in these buckets will "
                    "be unavailable until nodes come back up",
                    _oldState.getTextualDifference(_state).c_str(),
                    _removed_buckets, _removed_documents);
    }
}

} // distributor
