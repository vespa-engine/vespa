// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "changedbucketownershiphandler.h"
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/storage/common/messagebucket.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".bucketownershiphandler");

namespace storage {

ChangedBucketOwnershipHandler::ChangedBucketOwnershipHandler(
        const config::ConfigUri& configUri,
        ServiceLayerComponentRegister& compReg)
    : StorageLink("Changed bucket ownership handler"),
      _component(compReg, "changedbucketownershiphandler"),
      _metrics(),
      _configFetcher(configUri.getContext()),
      _stateLock(),
      _currentState(), // Not set yet, so ownership will not be valid
      _currentOwnership(std::make_shared<OwnershipState>(
            _component.getBucketSpaceRepo(), _currentState)),
      _abortQueuedAndPendingOnStateChange(false),
      _abortMutatingIdealStateOps(false),
      _abortMutatingExternalLoadOps(false)
{
    _configFetcher.subscribe<vespa::config::content::PersistenceConfig>(configUri.getConfigId(), this);
    _configFetcher.start();
    _component.registerMetric(_metrics);
}

ChangedBucketOwnershipHandler::~ChangedBucketOwnershipHandler()
{
}

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
    vespalib::LockGuard guard(_stateLock);
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
    : metrics::MetricSet("changedbucketownershiphandler", "", "", owner),
      averageAbortProcessingTime("avg_abort_processing_time", "", "Average time spent aborting operations for changed buckets", this),
      idealStateOpsAborted("ideal_state_ops_aborted", "", "Number of outdated ideal state operations aborted", this),
      externalLoadOpsAborted("external_load_ops_aborted", "", "Number of outdated external load operations aborted", this)
{}
ChangedBucketOwnershipHandler::Metrics::~Metrics() { }

ChangedBucketOwnershipHandler::OwnershipState::OwnershipState(const ContentBucketSpaceRepo &contentBucketSpaceRepo,
                                                              std::shared_ptr<const lib::ClusterStateBundle> state)
    : _distributions(),
      _state(state)
{
    for (const auto &elem : contentBucketSpaceRepo) {
        auto distribution = elem.second->getDistribution();
        if (distribution) {
            _distributions.emplace(elem.first, std::move(distribution));
        }
    }
}


ChangedBucketOwnershipHandler::OwnershipState::~OwnershipState() {}


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

    bool doShouldAbort(const document::Bucket &bucket) const override {
        if (_allDistributorsHaveGoneDown) {
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
            const ChangedBucketOwnershipHandler::OwnershipState& newState)
        : _oldState(oldState),
          _newState(newState),
          _allDistributorsHaveGoneDown(
                  allDistributorsDownInState(newState.getBaselineState()))
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
            new StateDiffLazyAbortPredicate(*oldOwnership, *newOwnership));
}

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
bool
ChangedBucketOwnershipHandler::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& stateCmd)
{
    if (!enabledOperationAbortingOnStateChange()) {
        LOG(debug, "Operation aborting is config-disabled");
        return false; // Early out.
    }
    OwnershipState::CSP oldOwnership;
    OwnershipState::CSP newOwnership;
    // Get old state and update own current cluster state _before_ it is
    // applied to the rest of the system. This helps ensure that no message
    // can get through in the off-case that the lower level storage links
    // don't apply the state immediately for some reason.
    {
        vespalib::LockGuard guard(_stateLock);
        oldOwnership = _currentOwnership;
        setCurrentOwnershipWithStateNoLock(stateCmd->getClusterStateBundle());
        newOwnership = _currentOwnership;
    }
    assert(newOwnership->valid());
    // If we're going from not having a state to having a state, we per
    // definition cannot possibly have gotten any load that needs aborting,
    // as no such load is allowed through this component when this is the
    // case.
    if (!oldOwnership->valid()) {
        return false;
    }

    if (allDistributorsDownInState(oldOwnership->getBaselineState())) {
        LOG(debug, "No need to send aborts on transition '%s' -> '%s'",
            oldOwnership->getBaselineState().toString().c_str(),
            newOwnership->getBaselineState().toString().c_str());
        return false;
    }
    logTransition(oldOwnership->getBaselineState(), newOwnership->getBaselineState());

    metrics::MetricTimer durationTimer;
    auto predicate(makeLazyAbortPredicate(oldOwnership, newOwnership));
    AbortBucketOperationsCommand::SP cmd(
            new AbortBucketOperationsCommand(std::move(predicate)));

    // Will not return until all operation aborts have been performed
    // on the lower level links, at which point it is safe to send down
    // the SetSystemStateCommand.
    sendDown(cmd);

    durationTimer.stop(_metrics.averageAbortProcessingTime);
    return false;
}

/**
 * Invoked whenever a distribution config change happens and is called in the
 * context of the config updater thread (which is why we have to lock).
 */
void
ChangedBucketOwnershipHandler::storageDistributionChanged()
{
    vespalib::LockGuard guard(_stateLock);
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
    vespalib::LockGuard guard(_stateLock);
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
        return onSetSystemState(
                std::static_pointer_cast<api::SetSystemStateCommand>(msg));
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

}
