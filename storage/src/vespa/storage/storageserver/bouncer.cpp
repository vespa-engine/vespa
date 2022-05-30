// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bouncer.h"
#include "bouncer_metrics.h"
#include "config_logging.h"
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

#include <vespa/log/log.h>
#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".bouncer");

namespace storage {

Bouncer::Bouncer(StorageComponentRegister& compReg, const config::ConfigUri & configUri)
    : StorageLink("Bouncer"),
      _config(new vespa::config::content::core::StorBouncerConfig()),
      _component(compReg, "bouncer"),
      _lock(),
      _baselineNodeState("s:i"),
      _derivedNodeStates(),
      _clusterState(&lib::State::UP),
      _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext())),
      _metrics(std::make_unique<BouncerMetrics>())
{
    _component.getStateUpdater().addStateListener(*this);
    _component.registerMetric(*_metrics);
    // Register for config. Normally not critical, so catching config
    // exception allowing program to continue if missing/faulty config.
    try{
        if (!configUri.empty()) {
            _configFetcher->subscribe<vespa::config::content::core::StorBouncerConfig>(configUri.getConfigId(), this);
            _configFetcher->start();
        } else {
            LOG(info, "No config id specified. Using defaults rather than "
                      "config");
        }
    } catch (config::InvalidConfigException& e) {
        LOG(info, "Bouncer failed to load config '%s'. This "
                  "is not critical since it has sensible defaults: %s",
            configUri.getConfigId().c_str(), e.what());
    }
}

Bouncer::~Bouncer()
{
    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());
}

void
Bouncer::print(std::ostream& out, bool verbose,
               const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "Bouncer(" << _baselineNodeState << ")";
}

void
Bouncer::onClose()
{
    _configFetcher->close();
    _component.getStateUpdater().removeStateListener(*this);
}

void
Bouncer::configure(std::unique_ptr<vespa::config::content::core::StorBouncerConfig> config)
{
    log_config_received(*config);
    validateConfig(*config);
    std::lock_guard lock(_lock);
    _config = std::move(config);
}

const BouncerMetrics& Bouncer::metrics() const noexcept {
    return *_metrics;
}

void
Bouncer::validateConfig(
        const vespa::config::content::core::StorBouncerConfig& newConfig) const
{
    if (newConfig.feedRejectionPriorityThreshold != -1) {
        if (newConfig.feedRejectionPriorityThreshold
            > std::numeric_limits<api::StorageMessage::Priority>::max())
        {
            throw config::InvalidConfigException(
                    "feed_rejection_priority_threshold config value exceeds "
                    "maximum allowed value");
        }
        if (newConfig.feedRejectionPriorityThreshold
            < std::numeric_limits<api::StorageMessage::Priority>::min())
        {
            throw config::InvalidConfigException(
                    "feed_rejection_priority_threshold config value lower than "
                    "minimum allowed value");
        }
    }
}

void Bouncer::append_node_identity(std::ostream& target_stream) const {
    target_stream << " (on " << _component.getNodeType() << '.' << _component.getIndex() << ")";
}

void
Bouncer::abortCommandForUnavailableNode(api::StorageMessage& msg,
                                        const lib::State& state)
{
    // If we're not up or retired, fail due to this nodes state.
    std::shared_ptr<api::StorageReply> reply(
            static_cast<api::StorageCommand&>(msg).makeReply().release());
    std::ostringstream ost;
    ost << "We don't allow command of type " << msg.getType()
        << " when node is in state " << state.toString(true);
    append_node_identity(ost);
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, ost.str()));
    _metrics->unavailable_node_aborts.inc();
    sendUp(reply);
}

void
Bouncer::rejectCommandWithTooHighClockSkew(api::StorageMessage& msg,
                                          int maxClockSkewInSeconds)
{
    auto& as_cmd = dynamic_cast<api::StorageCommand&>(msg);
    std::ostringstream ost;
    ost << "Message " << msg.getType() << " is more than "
        << maxClockSkewInSeconds << " seconds in the future, set up NTP.";
    append_node_identity(ost);
    LOGBP(warning, "Rejecting operation from distributor %u: %s",
          as_cmd.getSourceIndex(), ost.str().c_str());
    _metrics->clock_skew_aborts.inc();

    std::shared_ptr<api::StorageReply> reply(as_cmd.makeReply().release());
    reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, ost.str()));
    sendUp(reply);
}

void
Bouncer::abortCommandDueToClusterDown(api::StorageMessage& msg, const lib::State& cluster_state)
{
    std::shared_ptr<api::StorageReply> reply(
            static_cast<api::StorageCommand&>(msg).makeReply().release());
    std::ostringstream ost;
    ost << "We don't allow external load while cluster is in state "
        << cluster_state.toString(true);
    append_node_identity(ost);
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, ost.str()));
    sendUp(reply);
}

bool
Bouncer::clusterIsUp(const lib::State& cluster_state)
{
    return (cluster_state == lib::State::UP);
}

bool Bouncer::isDistributor() const {
    return (_component.getNodeType() == lib::NodeType::DISTRIBUTOR);
}

uint64_t
Bouncer::extractMutationTimestampIfAny(const api::StorageMessage& msg)
{
    switch (msg.getType().getId()) {
        case api::MessageType::PUT_ID:
            return static_cast<const api::PutCommand&>(msg).getTimestamp();
        case api::MessageType::REMOVE_ID:
            return static_cast<const api::RemoveCommand&>(msg).getTimestamp();
        case api::MessageType::UPDATE_ID:
            return static_cast<const api::UpdateCommand&>(msg).getTimestamp();
        default:
            return 0;
    }
}

bool
Bouncer::isExternalLoad(const api::MessageType& type) const noexcept
{
    switch (type.getId()) {
        case api::MessageType::PUT_ID:
        case api::MessageType::REMOVE_ID:
        case api::MessageType::UPDATE_ID:
        case api::MessageType::GET_ID:
        case api::MessageType::VISITOR_CREATE_ID:
        case api::MessageType::STATBUCKET_ID:
            return true;
        default:
            return false;
    }
}

bool
Bouncer::isExternalWriteOperation(const api::MessageType& type) const noexcept {
    switch (type.getId()) {
    case api::MessageType::PUT_ID:
    case api::MessageType::REMOVE_ID:
    case api::MessageType::UPDATE_ID:
        return true;
    default:
        return false;
    }
}

void
Bouncer::rejectDueToInsufficientPriority(
        api::StorageMessage& msg,
        api::StorageMessage::Priority feedPriorityLowerBound)
{
    std::shared_ptr<api::StorageReply> reply(
            static_cast<api::StorageCommand&>(msg).makeReply().release());
    std::ostringstream ost;
    ost << "Operation priority (" << int(msg.getPriority())
        << ") is lower than currently configured threshold ("
        << int(feedPriorityLowerBound) << ") -- note that lower numbers "
           "mean a higher priority. This usually means your application "
           "has been reconfigured to deal with a transient upgrade or "
           "load event";
    reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, ost.str()));
    sendUp(reply);
}

void
Bouncer::reject_due_to_too_few_bucket_bits(api::StorageMessage& msg) {
    std::shared_ptr<api::StorageReply> reply(
            dynamic_cast<api::StorageCommand&>(msg).makeReply());
    reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED,
                                     vespalib::make_string("Operation bucket %s has too few bits used (%u < minimum of %u)",
                                                           msg.getBucketId().toString().c_str(),
                                                           msg.getBucketId().getUsedBits(),
                                                           spi::BucketLimits::MinUsedBits)));
    sendUp(reply);
}

bool
Bouncer::onDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    const api::MessageType& type(msg->getType());
    // All replies can come in.
    if (type.isReply()) {
        return false;
    }

    switch (type.getId()) {
        case api::MessageType::SETNODESTATE_ID:
        case api::MessageType::GETNODESTATE_ID:
        case api::MessageType::SETSYSTEMSTATE_ID:
        case api::MessageType::ACTIVATE_CLUSTER_STATE_VERSION_ID:
        case api::MessageType::NOTIFYBUCKETCHANGE_ID:
            // state commands are always ok
            return false;
        default:
            break;
    }
    const lib::State* state;
    int maxClockSkewInSeconds;
    bool isInAvailableState;
    bool abortLoadWhenClusterDown;
    const lib::State *cluster_state;
    int feedPriorityLowerBound;
    {
        std::lock_guard lock(_lock);
        state                    = &getDerivedNodeState(msg->getBucket().getBucketSpace()).getState();
        maxClockSkewInSeconds    = _config->maxClockSkewSeconds;
        abortLoadWhenClusterDown = _config->stopExternalLoadWhenClusterDown;
        cluster_state            = _clusterState;
        isInAvailableState       = state->oneOf(_config->stopAllLoadWhenNodestateNotIn.c_str());
        feedPriorityLowerBound   = _config->feedRejectionPriorityThreshold;
    }
    // Special case for messages storage nodes are expected to get during
    // initializing. Request bucket info will be queued so storage can
    // answer them at the moment they are done initializing
    if (*state == lib::State::INITIALIZING &&
        type.getId() == api::MessageType::REQUESTBUCKETINFO_ID)
    {
        return false;
    }
    // Special case for point lookup Gets while node is in maintenance mode
    // to allow reads to complete during two-phase cluster state transitions
    if ((*state == lib::State::MAINTENANCE) && (type.getId() == api::MessageType::GET_ID) && clusterIsUp(*cluster_state)) {
        MBUS_TRACE(msg->getTrace(), 7, "Bouncer: node is in Maintenance mode, but letting Get through");
        return false;
    }

    const bool externalLoad = isExternalLoad(type);
    if (!isInAvailableState && !(isDistributor() && externalLoad)) {
        abortCommandForUnavailableNode(*msg, *state);
        return true;
    }

    // Allow all internal load to go through at this point
    if (!externalLoad) {
        return false;
    }
    if (priorityRejectionIsEnabled(feedPriorityLowerBound)
        && isExternalWriteOperation(type)
        && (msg->getPriority() > feedPriorityLowerBound))
    {
        rejectDueToInsufficientPriority(*msg, feedPriorityLowerBound);
        return true;
    }

    uint64_t timestamp = extractMutationTimestampIfAny(*msg);
    if (timestamp != 0) {
        timestamp /= 1000000;
        uint64_t currentTime = _component.getClock().getTimeInSeconds().getTime();
        if (timestamp > currentTime + maxClockSkewInSeconds) {
            rejectCommandWithTooHighClockSkew(*msg, maxClockSkewInSeconds);
            return true;
        }
    }

    // If cluster state is not up, fail external load
    if (abortLoadWhenClusterDown && !clusterIsUp(*cluster_state)) {
        abortCommandDueToClusterDown(*msg, *cluster_state);
        return true;
    }

    const auto bucket_id = msg->getBucketId();
    if ((bucket_id.getId() != 0) && (bucket_id.getUsedBits() < spi::BucketLimits::MinUsedBits)) {
        reject_due_to_too_few_bucket_bits(*msg);
        return true;
    }
    return false;
}

namespace {

lib::NodeState
deriveNodeState(const lib::NodeState &reportedNodeState,
                const lib::NodeState &currentNodeState)
{
    // If current node state is more strict than our own reported state,
    // set node state to our current state
    if (reportedNodeState.getState().maySetWantedStateForThisNodeState(currentNodeState.getState())) {
        return currentNodeState;
    }
    return reportedNodeState;
}

}

void
Bouncer::handleNewState()
{
    std::lock_guard lock(_lock);
    const auto reportedNodeState = *_component.getStateUpdater().getReportedNodeState();
    const auto clusterStateBundle = _component.getStateUpdater().getClusterStateBundle();
    const auto &clusterState = *clusterStateBundle->getBaselineClusterState();
    _clusterState = &clusterState.getClusterState();
    const lib::Node node(_component.getNodeType(), _component.getIndex());
    _baselineNodeState = deriveNodeState(reportedNodeState, clusterState.getNodeState(node));
    _derivedNodeStates.clear();
    for (const auto &derivedClusterState : clusterStateBundle->getDerivedClusterStates()) {
        _derivedNodeStates[derivedClusterState.first] =
            deriveNodeState(reportedNodeState, derivedClusterState.second->getNodeState(node));
    }
}

const lib::NodeState &
Bouncer::getDerivedNodeState(document::BucketSpace bucketSpace) const
{
    auto itr = _derivedNodeStates.find(bucketSpace);
    if (itr != _derivedNodeStates.end()) {
        return itr->second;
    } else {
        return _baselineNodeState;
    }
}

} // storage
