// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "changedbucketownershiphandler.h"
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/storage/common/messagebucket.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/metrics/metrictimer.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/configfetcher.hpp>



#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".bucketownershiphandler");

namespace storage {

ChangedBucketOwnershipHandler::ChangedBucketOwnershipHandler(
        const config::ConfigUri& configUri,
        ServiceLayerComponentRegister& compReg)
    : StorageLink("Changed bucket ownership handler"),
      _component(compReg, "changedbucketownershiphandler"),
      _metrics(),
      _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext())),
      _state_sync_executor(1), // single thread for sequential task execution
      _stateLock(),
      _currentState(), // Not set yet, so ownership will not be valid
      _currentOwnership(std::make_shared<OwnershipState>(
            _component.getBucketSpaceRepo(), _currentState)),
      _abortQueuedAndPendingOnStateChange(false),
      _abortMutatingIdealStateOps(false),
      _abortMutatingExternalLoadOps(false)
{
    _configFetcher->subscribe<vespa::config::content::PersistenceConfig>(configUri.getConfigId(), this);
    _configFetcher->start();
    _component.registerMetric(_metrics);
}

ChangedBucketOwnershipHandler::~ChangedBucketOwnershipHandler() = default;

void
ChangedBucketOwnershipHandler::configure(
        std::unique_ptr<vespa::config::content::PersistenceConfig> config)
{
    _abortQueuedAndPendingOnStateChange.store(
            config->abortOperationsWithChangedBucketOwnership,
            std::memory_order_relaxed);
    _abortMutatingIdealStateOps.store(
            config->abortOutdatedMutatingIdealStateOps,
            std::memory_order_relaxed);
    _abortMutatingExternalLoadOps.store(
            config->abortOutdatedMutatingExternalLoadOps,
            std::memory_order_relaxed);
}

void
ChangedBucketOwnershipHandler::reloadClusterState()
{
    std::lock_guard guard(_stateLock);
    const auto clusterStateBundle = _component.getStateUpdater().getClusterStateBundle();
    setCurrentOwnershipWithStateNoLock(*clusterStateBundle);
}

void
ChangedBucketOwnershipHandler::setCurrentOwnershipWithStateNoLock(
        const lib::ClusterStateBundle& newState)
{
    _currentState = std::make_shared<const lib::ClusterStateBundle>(newState);
    _currentOwnership = std::make_shared<const OwnershipState>(
            _component.getBucketSpaceRepo(), _currentState);
}

namespace {

bool
allDistributorsDownInState(const lib::ClusterState& state) {
    using lib::NodeType;
    using lib::Node;
    uint16_t nodeCount(state.getNodeCount(NodeType::DISTRIBUTOR));
    for (uint16_t i = 0; i < nodeCount; ++i) {
        if (state.getNodeState(Node(NodeType::DISTRIBUTOR, i)).getState().oneOf("ui")) {
            return false;
        }
    }
    return true;
}

}

ChangedBucketOwnershipHandler::Metrics::Metrics(metrics::MetricSet* owner)
    : metrics::MetricSet("changedbucketownershiphandler", {}, "", owner),
      averageAbortProcessingTime("avg_abort_processing_time", {}, "Average time spent aborting operations for changed buckets", this),
      idealStateOpsAborted("ideal_state_ops_aborted", {}, "Number of outdated ideal state operations aborted", this),
      externalLoadOpsAborted("external_load_ops_aborted", {}, "Number of outdated external load operations aborted", this)
{}
ChangedBucketOwnershipHandler::Metrics::~Metrics() = default;

ChangedBucketOwnershipHandler::OwnershipState::OwnershipState(const ContentBucketSpaceRepo &contentBucketSpaceRepo,
                                                              std::shared_ptr<const lib::ClusterStateBundle> state)
    : _distributions(),
      _state(std::move(state))
{
    for (const auto &elem : contentBucketSpaceRepo) {
        auto distribution = elem.second->getDistribution();
        if (distribution) {
            _distributions.emplace(elem.first, std::move(distribution));
        }
    }
}


ChangedBucketOwnershipHandler::OwnershipState::~OwnershipState() = default;


const lib::ClusterState&
ChangedBucketOwnershipHandler::OwnershipState::getBaselineState() const
{
    assert(valid());
    return *_state->getBaselineClusterState();
}

uint16_t
ChangedBucketOwnershipHandler::OwnershipState::ownerOf(
        const document::Bucket& bucket) const
{
    auto distributionItr = _distributions.find(bucket.getBucketSpace());
    assert(distributionItr != _distributions.end());
    const auto &distribution = *distributionItr->second;
    const auto &derivedState = *_state->getDerivedClusterState(bucket.getBucketSpace());
    try {
        return distribution.getIdealDistributorNode(derivedState, bucket.getBucketId());
    } catch (lib::TooFewBucketBitsInUseException& e) {
        LOGBP(debug,
              "Too few bucket bits used for %s to be assigned to "
              "a distributor.",
              bucket.toString().c_str());
    } catch (lib::NoDistributorsAvailableException& e) {
        LOGBP(warning,
              "Got exception with no distributors available when checking "
              "bucket owner; this should not happen as we explicitly check "
              "for available distributors before reaching this code path! "
              "Cluster state is '%s', distribution is '%s'",
              derivedState.toString().c_str(),
              distribution.toString().c_str());
    } catch (const std::exception& e) {
        LOG(error,
            "Got unknown exception while resolving distributor: %s",
            e.what());
    }
    return FAILED_TO_RESOLVE;
}

bool
ChangedBucketOwnershipHandler::OwnershipState::storageNodeUp(document::BucketSpace bucketSpace, uint16_t nodeIndex) const
{
    const auto &derivedState = *_state->getDerivedClusterState(bucketSpace);
    lib::Node node(lib::NodeType::STORAGE, nodeIndex);
    return derivedState.getNodeState(node).getState().oneOf("uir");
}

void
ChangedBucketOwnershipHandler::logTransition(
        const lib::ClusterState& currentState,
        const lib::ClusterState& newState) const
{
    LOG(debug,
        "State transition '%s' -> '%s' changes distributor bucket ownership, "
        "so must abort queued operations for the affected buckets.",
        currentState.toString().c_str(),
        newState.toString().c_str());
}

namespace {

class StateDiffLazyAbortPredicate
    : public AbortBucketOperationsCommand::AbortPredicate
{
    // Ownership states wrap a couple of shared_ptrs and are thus cheap to
    // copy and store.
    ChangedBucketOwnershipHandler::OwnershipState _oldState;
    ChangedBucketOwnershipHandler::OwnershipState _newState;
    // Fast path to avoid trying (and failing) to compute owner in a state
    // where all distributors are down.
    bool _allDistributorsHaveGoneDown;
    uint16_t _nodeIndex;

    bool contentNodeUpInBucketSpace(document::BucketSpace bucketSpace) const {
        return _newState.storageNodeUp(bucketSpace, _nodeIndex);
    }

    bool doShouldAbort(const document::Bucket &bucket) const override {
        if (_allDistributorsHaveGoneDown) {
            return true;
        }
        if (!contentNodeUpInBucketSpace(bucket.getBucketSpace())) {
            return true;
        }
        uint16_t oldOwner(_oldState.ownerOf(bucket));
        uint16_t newOwner(_newState.ownerOf(bucket));
        if (oldOwner != newOwner) {
            LOG(spam, "Owner of %s was %u, now %u. Operation should be aborted",
                bucket.toString().c_str(), oldOwner, newOwner);
            return true;
        }
        return false;
    }
public:
    StateDiffLazyAbortPredicate(
            const ChangedBucketOwnershipHandler::OwnershipState& oldState,
            const ChangedBucketOwnershipHandler::OwnershipState& newState,
            uint16_t nodeIndex)
        : _oldState(oldState),
          _newState(newState),
          _allDistributorsHaveGoneDown(
                  allDistributorsDownInState(newState.getBaselineState())),
          _nodeIndex(nodeIndex)
    {
    }
};

}

std::unique_ptr<AbortBucketOperationsCommand::AbortPredicate>
ChangedBucketOwnershipHandler::makeLazyAbortPredicate(
        const OwnershipState::CSP& oldOwnership,
        const OwnershipState::CSP& newOwnership) const
{
    return std::unique_ptr<AbortBucketOperationsCommand::AbortPredicate>(
            new StateDiffLazyAbortPredicate(*oldOwnership, *newOwnership,
                                            _component.getIndex()));
}

class ChangedBucketOwnershipHandler::ClusterStateSyncAndApplyTask
    : public vespalib::Executor::Task
{
    ChangedBucketOwnershipHandler& _owner;
    std::shared_ptr<api::SetSystemStateCommand> _command;
public:
    ClusterStateSyncAndApplyTask(ChangedBucketOwnershipHandler& owner,
                                 std::shared_ptr<api::SetSystemStateCommand> command) noexcept
        : _owner(owner),
          _command(std::move(command))
    {}

    /*
     * If we go from:
     * 1) Not all down -> all distributors down
     *      - abort ops for _all_ buckets
     * 2) All distributors down -> not down
     *      - no-op, since down edge must have been handled first
     * 3) All down -> all down
     *      - no-op
     * 4) Some nodes down or up
     *      - abort ops for buckets that have changed ownership between
     *        current and new cluster state.
     */
    void run() override {
        OwnershipState::CSP old_ownership;
        OwnershipState::CSP new_ownership;
        // Update the ownership state inspected by all bucket-mutating operations passing through
        // this component so that messages from outdated distributors will be rejected. Note that
        // this is best-effort; with our current multitude of RPC threads directly dispatching
        // operations into the persistence provider, it's possible for a thread carrying an outdated
        // operation to have already passed the barrier, but be preempted so that it will apply the
        // op _after_ the abort step has completed.
        {
            std::lock_guard guard(_owner._stateLock);
            old_ownership = _owner._currentOwnership;
            _owner.setCurrentOwnershipWithStateNoLock(_command->getClusterStateBundle());
            new_ownership = _owner._currentOwnership;
        }
        assert(new_ownership->valid());
        // If we're going from not having a state to having a state, we per
        // definition cannot possibly have gotten any load that needs aborting,
        // as no such load is allowed through this component when this is the
        // case.
        if (!old_ownership->valid()) {
            return _owner.sendDown(_command);
        }

        if (allDistributorsDownInState(old_ownership->getBaselineState())) {
            LOG(debug, "No need to send aborts on transition '%s' -> '%s'",
                old_ownership->getBaselineState().toString().c_str(),
                new_ownership->getBaselineState().toString().c_str());
            return _owner.sendDown(_command);;
        }
        _owner.logTransition(old_ownership->getBaselineState(), new_ownership->getBaselineState());

        metrics::MetricTimer duration_timer;
        auto predicate = _owner.makeLazyAbortPredicate(old_ownership, new_ownership);
        auto abort_cmd = std::make_shared<AbortBucketOperationsCommand>(std::move(predicate));

        // Will not return until all operation aborts have been performed
        // on the lower level links, at which point it is safe to send down
        // the SetSystemStateCommand.
        _owner.sendDown(abort_cmd);
        duration_timer.stop(_owner._metrics.averageAbortProcessingTime);

        // Conflicting operations have been aborted and incoming conflicting operations
        // are aborted inline; send down the state command actually making the state change
        // visible on the content node.
        _owner.sendDown(_command);
    }
};

bool
ChangedBucketOwnershipHandler::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& stateCmd)
{
    if (!enabledOperationAbortingOnStateChange()) {
        LOG(debug, "Operation aborting is config-disabled");
        return false; // Early out.
    }
    // Dispatch to background worker. This indirection is because operations such as lid-space compaction
    // may cause the implicit operation abort waiting step to block the caller for a relatively long time.
    // It is very important that the executor only has 1 thread, which means this has FIFO behavior.
    [[maybe_unused]] auto rejected_task = _state_sync_executor.execute(std::make_unique<ClusterStateSyncAndApplyTask>(*this, stateCmd));
    // If this fails, we have processed a message _after_ onClose has been called, which should not happen.
    assert(!rejected_task);
    return true;
}

/**
 * Invoked whenever a distribution config change happens and is called in the
 * context of the config updater thread (which is why we have to lock).
 */
void
ChangedBucketOwnershipHandler::storageDistributionChanged()
{
    std::lock_guard guard(_stateLock);
    _currentOwnership = std::make_shared<OwnershipState>(
            _component.getBucketSpaceRepo(), _currentState);
}

bool
ChangedBucketOwnershipHandler::isMutatingIdealStateOperation(
        const api::StorageMessage& msg) const
{
    switch (msg.getType().getId()) {
    case api::MessageType::CREATEBUCKET_ID:
    case api::MessageType::MERGEBUCKET_ID:
    case api::MessageType::DELETEBUCKET_ID:
    case api::MessageType::SPLITBUCKET_ID:
    case api::MessageType::JOINBUCKETS_ID:
    // Note: RemoveLocation is external load, but is used to implement GC and
    // must thus be treated as an ideal state operation for that purpose.
    case api::MessageType::REMOVELOCATION_ID:
    case api::MessageType::SETBUCKETSTATE_ID:
        return true;
    default:
        return false;
    }
}


bool
ChangedBucketOwnershipHandler::isMutatingExternalOperation(
        const api::StorageMessage& msg) const
{
    switch (msg.getType().getId()) {
    case api::MessageType::PUT_ID:
    case api::MessageType::REMOVE_ID:
    case api::MessageType::UPDATE_ID:
    case api::MessageType::REVERT_ID:
        return true;
    default:
        return false;
    }
}

ChangedBucketOwnershipHandler::OwnershipState::CSP
ChangedBucketOwnershipHandler::getCurrentOwnershipState() const
{
    std::lock_guard guard(_stateLock);
    return _currentOwnership;
}

bool
ChangedBucketOwnershipHandler::sendingDistributorOwnsBucketInCurrentState(
        const api::StorageCommand& cmd) const
{
    OwnershipState::CSP current(getCurrentOwnershipState());
    if (!current->valid()) {
        LOG(debug, "No cluster state received yet, must bounce message '%s'",
            cmd.toString().c_str());
        return false;
    }

    try {
        document::Bucket opBucket(getStorageMessageBucket(cmd));
        return (current->ownerOf(opBucket) == cmd.getSourceIndex());
    } catch (vespalib::IllegalArgumentException& e) {
        LOG(error,
            "Precondition violation: unable to get bucket from "
            "message: %s",
            e.toString().c_str());
        assert(false);
    }
    return false; // Unreachable statement.
}

void
ChangedBucketOwnershipHandler::abortOperation(api::StorageCommand& cmd)
{
    api::StorageReply::SP reply(cmd.makeReply());
    reply->setResult(api::ReturnCode(
            api::ReturnCode::ABORTED,
            "Operation aborted to prevent inconsistencies caused by a "
            "change in bucket ownership"));
    sendUp(reply);
    if (isMutatingIdealStateOperation(cmd)) {
        _metrics.idealStateOpsAborted.inc();
    } else {
        _metrics.externalLoadOpsAborted.inc();
    }
}

bool
ChangedBucketOwnershipHandler::isMutatingCommandAndNeedsChecking(
        const api::StorageMessage& msg) const
{
    if (enabledIdealStateAborting() && isMutatingIdealStateOperation(msg)) {
        return true;
    }
    if (enabledExternalLoadAborting() && isMutatingExternalOperation(msg)) {
        return true;
    }
    return false;
}

bool
ChangedBucketOwnershipHandler::onDown(
        const std::shared_ptr<api::StorageMessage>& msg)
{
    if (msg->getType() == api::MessageType::SETSYSTEMSTATE) {
        return onSetSystemState(std::static_pointer_cast<api::SetSystemStateCommand>(msg));
    }
    if (!isMutatingCommandAndNeedsChecking(*msg)) {
        return false;
    }
    api::StorageCommand& cmd(static_cast<api::StorageCommand&>(*msg));
    if (!sendingDistributorOwnsBucketInCurrentState(cmd)) {
        abortOperation(cmd);
        return true;
    }
    return false;
}

bool
ChangedBucketOwnershipHandler::enabledOperationAbortingOnStateChange() const
{
    return _abortQueuedAndPendingOnStateChange.load(std::memory_order_relaxed);
}

bool
ChangedBucketOwnershipHandler::enabledIdealStateAborting() const
{
    return _abortMutatingIdealStateOps.load(std::memory_order_relaxed);
}

bool
ChangedBucketOwnershipHandler::enabledExternalLoadAborting() const
{
    return _abortMutatingExternalLoadOps.load(std::memory_order_relaxed);
}

bool
ChangedBucketOwnershipHandler::onInternalReply(
        const std::shared_ptr<api::InternalReply>& reply)
{
    // Just swallow reply, we don't do anything with it.
    return (reply->getType() == AbortBucketOperationsReply::ID);
}

void
ChangedBucketOwnershipHandler::onClose()
{
    _state_sync_executor.shutdown().sync();
}

}
