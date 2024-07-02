// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statemanager.h"
#include "storagemetricsset.h"
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/defaults.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/metrictimer.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/string_escape.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <fstream>
#include <ranges>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".state.manager");

using vespalib::make_string_short::fmt;

namespace storage {

namespace {
constexpr vespalib::duration MAX_TIMEOUT = 600s;

[[nodiscard]] std::shared_ptr<const lib::ClusterStateBundle>
make_bootstrap_state_bundle(std::shared_ptr<const lib::Distribution> config) {
    return std::make_shared<const lib::ClusterStateBundle>(
            std::make_shared<lib::ClusterState>(),
            lib::ClusterStateBundle::BucketSpaceStateMapping{},
            std::nullopt,
            lib::DistributionConfigBundle::of(std::move(config)),
            false);
}

}

struct StateManager::StateManagerMetrics : metrics::MetricSet {
    metrics::DoubleAverageMetric invoke_state_listeners_latency;

    explicit StateManagerMetrics(metrics::MetricSet* owner = nullptr)
        : metrics::MetricSet("state_manager", {}, "", owner),
          invoke_state_listeners_latency("invoke_state_listeners_latency", {},
                                         "Time spent (in ms) propagating state changes to internal state listeners", this)
    {}

    ~StateManagerMetrics() override;
};

StateManager::StateManagerMetrics::~StateManagerMetrics() = default;

using lib::ClusterStateBundle;

StateManager::StateManager(StorageComponentRegister& compReg,
                           std::unique_ptr<HostInfo> hostInfo,
                           const NodeStateReporter & reporter,
                           bool testMode)
    : StorageLink("State manager"),
      framework::HtmlStatusReporter("systemstate", "Node and system state"),
      _component(compReg, "statemanager"),
      _nodeStateReporter(reporter),
      _metrics(std::make_unique<StateManagerMetrics>()),
      _stateLock(),
      _stateCond(),
      _listenerLock(),
      _nodeState(std::make_shared<lib::NodeState>(_component.getNodeType(), lib::State::DOWN)),
      _nextNodeState(),
      _configured_distribution(_component.getDistribution()),
      _systemState(make_bootstrap_state_bundle(_configured_distribution)),
      _nextSystemState(),
      _reported_host_info_cluster_state_version(0),
      _stateListeners(),
      _queuedStateRequests(),
      _threadLock(),
      _systemStateHistory(),
      _systemStateHistorySize(50),
      _start_time(vespalib::steady_clock::now()),
      _health_ping_time(),
      _health_ping_warn_interval(5min),
      _health_ping_warn_time(_start_time + _health_ping_warn_interval),
      _last_accepted_cluster_state_time(),
      _last_observed_version_from_cc(),
      _hostInfo(std::move(hostInfo)),
      _controllers_observed_explicit_node_state(),
      _noThreadTestMode(testMode),
      _grabbedExternalLock(false),
      _require_strictly_increasing_cluster_state_versions(false),
      _receiving_distribution_config_from_cc(false),
      _notifyingListeners(false),
      _requested_almost_immediate_node_state_replies(false)
{
    _nodeState->setMinUsedBits(58);
    _nodeState->setStartTimestamp(vespalib::count_s(_component.getClock().getSystemTime().time_since_epoch()));
    _component.registerStatusPage(*this);
    _component.registerMetric(*_metrics);
}

StateManager::~StateManager()
{
    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());
    if (_thread) {
        LOG(debug, "onClose() not called before destructor");
        _thread->interruptAndJoin(_threadCond);
    }
}

void
StateManager::onOpen()
{
    if (!_noThreadTestMode) {
        _thread = _component.startThread(*this, 30s);
    }
}

void
StateManager::onClose()
{
    if (_thread) {
        _thread->interruptAndJoin(_threadCond);
        _thread.reset();
    }
    sendGetNodeStateReplies();
}

void
StateManager::print(std::ostream& out, bool, const std::string& ) const
{
    out << "StateManager()";
}

void
StateManager::reportHtmlStatus(std::ostream& out,
                               const framework::HttpUrlPath&) const
{
    using vespalib::xml_content_escaped;
    {
        std::lock_guard lock(_stateLock);
        const auto& baseLineClusterState = _systemState->getBaselineClusterState();
        out << "<h1>Current system state</h1>\n"
            << "<code>" << xml_content_escaped(baseLineClusterState->toString(true)) << "</code>\n"
            << "<h1>Current node state</h1>\n"
            << "<code>" << baseLineClusterState->getNodeState(lib::Node(
                        _component.getNodeType(), _component.getIndex())).toString(true)
            << "</code>\n"
            << "<h1>Reported node state</h1>\n"
            << "<code>" << xml_content_escaped(_nodeState->toString(true)) << "</code>\n"
            << "<h1>Pending state requests</h1>\n"
            << _queuedStateRequests.size() << "\n"
            << "<h1>System state history</h1>\n"
            << "<table border=\"1\"><tr>"
            << "<th>Received at time</th><th>State</th></tr>\n";
        for (const auto & it : std::ranges::reverse_view(_systemStateHistory)) {
            out << "<tr><td>" << vespalib::to_string(vespalib::to_utc(it.first)) << "</td><td>"
                << xml_content_escaped(it.second->getBaselineClusterState()->toString()) << "</td></tr>\n";
        }
        out << "</table>\n";
    }
}

lib::Node
StateManager::thisNode() const
{
    return { _component.getNodeType(), _component.getIndex() };
}

lib::NodeState::CSP
StateManager::getReportedNodeState() const
{
    std::lock_guard lock(_stateLock);
    return _nodeState;
}

lib::NodeState::CSP
StateManager::getCurrentNodeState() const
{
    std::lock_guard lock(_stateLock);
    return std::make_shared<const lib::NodeState>(_systemState->getBaselineClusterState()->getNodeState(thisNode()));
}

std::shared_ptr<const lib::ClusterStateBundle>
StateManager::getClusterStateBundle() const
{
    std::lock_guard lock(_stateLock);
    return _systemState;
}

// TODO remove when distribution config is only received from cluster controller
void
StateManager::storageDistributionChanged()
{
    {
        std::lock_guard lock(_stateLock);
        _configured_distribution = _component.getDistribution();
        if (_receiving_distribution_config_from_cc) {
            return; // nothing more to do
        }
        // Avoid losing any pending state if this callback happens in the middle of a
        // state update. This edge case is practically impossible to unit test today...
        const auto patch_state = _nextSystemState ? _nextSystemState : _systemState;
        _nextSystemState = patch_state->clone_with_new_distribution(
                lib::DistributionConfigBundle::of(_configured_distribution));
    }
    // We've assembled a new state bundle based on the (non-distribution carrying) state
    // bundle from the cluster controller and our own internal config. Propagate it as one
    // unit to the internal components.
    notifyStateListeners();
}

void
StateManager::addStateListener(StateListener& listener)
{
    std::lock_guard lock(_listenerLock);
    _stateListeners.push_back(&listener);
}

void
StateManager::removeStateListener(StateListener& listener)
{
    std::lock_guard lock(_listenerLock);
    for (auto it = _stateListeners.begin(); it != _stateListeners.end();) {
        if (*it == &listener) {
            it = _stateListeners.erase(it);
        } else {
            ++it;
        }
    }
}

struct StateManager::ExternalStateLock : public NodeStateUpdater::Lock {
    StateManager& _manager;

    explicit ExternalStateLock(StateManager& manager) noexcept : _manager(manager) {}
    ~ExternalStateLock() override {
        {
            std::lock_guard lock(_manager._stateLock);
            _manager._grabbedExternalLock = false;
        }
        _manager._stateCond.notify_all();
        _manager.notifyStateListeners();
    }
};

NodeStateUpdater::Lock::SP
StateManager::grabStateChangeLock()
{
    std::unique_lock guard(_stateLock);
    while (_grabbedExternalLock || _nextNodeState.get()) {
        _stateCond.wait(guard);
    }
    _grabbedExternalLock = true;
    return std::make_shared<ExternalStateLock>(*this);
}

void
StateManager::setReportedNodeState(const lib::NodeState& state)
{
    std::lock_guard lock(_stateLock);
    if (!_grabbedExternalLock) {
        LOG(error, "Cannot set reported node state without first having grabbed external lock");
        assert(false);
    }
    LOG(debug, "Adjusting reported node state to %s -> %s",
        _nodeState->toString().c_str(), state.toString().c_str());
    _nextNodeState = std::make_shared<lib::NodeState>(state);
}

/**
 * Allows reentrent calls, in case a listener calls setNodeState or similar.
 * We solve this by detecting that we're already notifying listeners, and then
 * doing it over and over again until noone alters the state in the callback.
 */
void
StateManager::notifyStateListeners()
{
    using lib::State;
    if (_notifyingListeners) {
        return;
    }
    std::lock_guard listenerLock(_listenerLock);
    _notifyingListeners = true;
    lib::NodeState::SP newState;
    while (true) {
        {
            std::lock_guard guard(_stateLock);
            if (!_nextNodeState && !_nextSystemState) {
                _notifyingListeners = false;
                _stateCond.notify_all();
                break; // No change
            }
            if (_nextNodeState) {
                assert(_nextNodeState->getState() != State::INITIALIZING);
                newState   = _nextNodeState;
                _nodeState = _nextNodeState;
                _nextNodeState.reset();
            }
            if (_nextSystemState) {
                enableNextClusterState();
            }
            _stateCond.notify_all();
        }
        metrics::MetricTimer handler_latency_timer;
        for (auto* listener : _stateListeners) {
            listener->handleNewState();
            // If one of them actually altered the state again, abort
            // sending events, update states and send new one to all.
            std::lock_guard guard(_stateLock);
            if (_nextNodeState || _nextSystemState) {
                break;
            }
        }
        handler_latency_timer.stop(_metrics->invoke_state_listeners_latency);
    }
    if (newState) {
        sendGetNodeStateReplies();
    }
    _notifyingListeners = false;
}

void
StateManager::enableNextClusterState()
{
    if (_systemStateHistory.size() >= _systemStateHistorySize) {
        _systemStateHistory.pop_front();
    }
    // _systemState must be non-null due to being initially set to an empty,
    // new cluster state upon construction and because it can only be
    // overwritten by a non-null pending cluster state afterwards.
    logNodeClusterStateTransition(*_systemState, *_nextSystemState);
    _systemState = _nextSystemState;
    if (!_nextSystemState->deferredActivation()) {
        _reported_host_info_cluster_state_version = _systemState->getVersion();
    } // else: reported version updated upon explicit activation edge
    _nextSystemState.reset();
    _systemStateHistory.emplace_back(_component.getClock().getMonotonicTime(), _systemState);
}

namespace {

using BucketSpaceToTransitionString = std::unordered_map<document::BucketSpace,
                                                         vespalib::string,
                                                         document::BucketSpace::hash>;

void
considerInsertDerivedTransition(const lib::State& currentBaseline,
                                const lib::State& newBaseline,
                                const lib::State& currentDerived,
                                const lib::State& newDerived,
                                const document::BucketSpace& bucketSpace,
                                BucketSpaceToTransitionString& transitions)
{
    bool considerDerivedTransition = ((currentDerived != newDerived) &&
            ((currentDerived != currentBaseline) || (newDerived != newBaseline)));
    if (considerDerivedTransition && (transitions.find(bucketSpace) == transitions.end())) {
        transitions[bucketSpace] = fmt("%s space: '%s' to '%s'",
                                       document::FixedBucketSpaces::to_string(bucketSpace).data(),
                                       currentDerived.getName().c_str(), newDerived.getName().c_str());
    }
}

BucketSpaceToTransitionString
calculateDerivedClusterStateTransitions(const ClusterStateBundle& currentState,
                                        const ClusterStateBundle& newState,
                                        const lib::Node node)
{
    BucketSpaceToTransitionString result;
    const lib::State& currentBaseline = currentState.getBaselineClusterState()->getNodeState(node).getState();
    const lib::State& newBaseline = newState.getBaselineClusterState()->getNodeState(node).getState();
    for (const auto& entry : currentState.getDerivedClusterStates()) {
        const lib::State& currentDerived = entry.second->getNodeState(node).getState();
        const lib::State& newDerived = newState.getDerivedClusterState(entry.first)->getNodeState(node).getState();
        considerInsertDerivedTransition(currentBaseline, newBaseline, currentDerived, newDerived, entry.first, result);
    }
    for (const auto& entry : newState.getDerivedClusterStates()) {
        const lib::State& newDerived = entry.second->getNodeState(node).getState();
        const lib::State& currentDerived = currentState.getDerivedClusterState(entry.first)->getNodeState(node).getState();
        considerInsertDerivedTransition(currentBaseline, newBaseline, currentDerived, newDerived, entry.first, result);
    }
    return result;
}

vespalib::string
transitionsToString(const BucketSpaceToTransitionString& transitions)
{
    if (transitions.empty()) {
        return "";
    }
    vespalib::asciistream stream;
    stream << "[";
    bool first = true;
    for (const auto& entry : transitions) {
        if (!first) {
            stream << ", ";
        }
        stream << entry.second;
        first = false;
    }
    stream << "] ";
    return stream.str();
}

}

void
StateManager::logNodeClusterStateTransition(const ClusterStateBundle& currentState,
                                            const ClusterStateBundle& newState) const
{
    lib::Node self(thisNode());
    const lib::State& before(currentState.getBaselineClusterState()->getNodeState(self).getState());
    const lib::State& after(newState.getBaselineClusterState()->getNodeState(self).getState());
    auto derivedTransitions = calculateDerivedClusterStateTransitions(currentState, newState, self);
    if ((before != after) || !derivedTransitions.empty()) {
        LOG(info, "Transitioning from baseline state '%s' to '%s' %s (cluster state version %u)",
            before.getName().c_str(), after.getName().c_str(),
            transitionsToString(derivedTransitions).c_str(), newState.getVersion());
    }
}

bool
StateManager::onGetNodeState(const api::GetNodeStateCommand::SP& cmd)
{
    bool sentReply = false;
    if (cmd->getSourceIndex() != 0xffff) {
        sentReply = sendGetNodeStateReplies(cmd->getSourceIndex());
    }
    std::shared_ptr<api::GetNodeStateReply> reply;
    {
        std::unique_lock guard(_stateLock);
        _health_ping_time = vespalib::steady_clock::now();
        _health_ping_warn_time = _health_ping_time.value() + _health_ping_warn_interval;
        const bool is_up_to_date = (_controllers_observed_explicit_node_state.find(cmd->getSourceIndex())
                                    != _controllers_observed_explicit_node_state.end());
        if ((cmd->getExpectedState() != nullptr)
            && (*cmd->getExpectedState() == *_nodeState || sentReply)
            && is_up_to_date)
        {
            vespalib::duration timeout = cmd->getTimeout();
            if (timeout == vespalib::duration::max()) timeout = MAX_TIMEOUT;

            LOG(debug, "Received get node state request with timeout of %f seconds. Scheduling to be answered in "
                       "%f seconds unless a node state change happens before that time.",
                vespalib::to_s(timeout), vespalib::to_s(timeout)*0.8);
            _queuedStateRequests.emplace_back(_component.getClock().getMonotonicTime() + timeout, cmd);
        } else {
            LOG(debug, "Answered get node state(sourceindex=%x) is%s up-to-date request right away since it thought we "
                       "were in node state %s, while our actual node state is currently %s and we didn't just reply to "
                       "existing request. %lu queued requests",
                cmd->getSourceIndex(), is_up_to_date ? "" : " NOT",
                cmd->getExpectedState() == nullptr ? "unknown": cmd->getExpectedState()->toString().c_str(),
                _nodeState->toString().c_str(), _queuedStateRequests.size());
            reply = std::make_shared<api::GetNodeStateReply>(*cmd, *_nodeState);
            mark_controller_as_having_observed_explicit_node_state(guard, cmd->getSourceIndex());
            guard.unlock();
            reply->setNodeInfo(getNodeInfo());
        }
    }
    if (reply) {
        sendUp(reply);
    }
    return true;
}

void
StateManager::mark_controller_as_having_observed_explicit_node_state(const std::unique_lock<std::mutex>&, uint16_t controller_index) {
    _controllers_observed_explicit_node_state.emplace(controller_index);
}

std::optional<uint32_t>
StateManager::try_set_cluster_state_bundle(std::shared_ptr<const ClusterStateBundle> c,
                                           uint16_t origin_controller_index)
{
    {
        std::lock_guard lock(_stateLock);
        uint32_t effective_active_version = (_nextSystemState ? _nextSystemState->getVersion()
                                                              : _systemState->getVersion());
        const auto now = _component.getClock().getMonotonicTime();
        const uint32_t last_ver_from_cc = _last_observed_version_from_cc[origin_controller_index];
        _last_observed_version_from_cc[origin_controller_index] = c->getVersion();

        if (_require_strictly_increasing_cluster_state_versions && (c->getVersion() < effective_active_version)) {
            if (c->getVersion() >= last_ver_from_cc) {
                constexpr auto reject_warn_threshold = 30s;
                if (now - _last_accepted_cluster_state_time <= reject_warn_threshold) {
                    LOG(debug, "Rejecting cluster state with version %u from cluster controller %u, as "
                               "we've already accepted version %u. Recently accepted another cluster state, "
                               "so assuming transient CC leadership period overlap.",
                        c->getVersion(), origin_controller_index, effective_active_version);
                } else {
                    // Rejections have happened for some time. Make a bit of noise.
                    LOGBP(warning, "Rejecting cluster state with version %u from cluster controller %u, as "
                                   "we've already accepted version %u.",
                          c->getVersion(), origin_controller_index, effective_active_version);
                }
                return {effective_active_version};
            } else {
                // SetSystemState RPCs are FIFO-ordered and a particular CC should enforce strictly increasing
                // cluster state versions through its ZooKeeper quorum (but commands may be resent for a given
                // version). This means that commands should contain _monotonically increasing_ versions from
                // a given CC origin index.
                // If this is _not_ the case, it indicates ZooKeeper state on the CCs has been lost or wiped,
                // at which point we have no other realistic choice than to accept the version, or the system
                // will stall until an operator manually intervenes by restarting the content cluster.
                LOG(error, "Received cluster state version %u from cluster controller %u, which is lower than "
                           "the current state version (%u) and the last received version (%u) from the same controller. "
                           "This indicates loss of cluster controller ZooKeeper state; accepting lower version to "
                           "prevent content cluster operations from stalling for an indeterminate amount of time.",
                    c->getVersion(), origin_controller_index, effective_active_version, last_ver_from_cc);
                // Fall through to state acceptance.
            }
        }
        _last_accepted_cluster_state_time = now;
        _receiving_distribution_config_from_cc = c->has_distribution_config();
        if (!c->has_distribution_config()) {
            LOG(debug, "Next state bundle '%s' does not have distribution config; patching in existing config '%s'",
                c->toString().c_str(), _configured_distribution->getNodeGraph().getDistributionConfigHash().c_str());
            _nextSystemState = c->clone_with_new_distribution(lib::DistributionConfigBundle::of(_configured_distribution));
        } else {
            LOG(debug, "Next state bundle is '%s'", c->toString().c_str());
            // TODO print what's changed in distribution config?
            _nextSystemState = std::move(c);
        }
    }
    notifyStateListeners();
    return std::nullopt;
}

bool
StateManager::onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    auto reply = std::make_shared<api::SetSystemStateReply>(*cmd);
    const auto maybe_rejected_by_ver = try_set_cluster_state_bundle(cmd->cluster_state_bundle_ptr(), cmd->getSourceIndex());
    if (maybe_rejected_by_ver) {
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED,
                                         fmt("Cluster state version %u rejected; node already has a higher cluster state version (%u)",
                                             cmd->getClusterStateBundle().getVersion(), *maybe_rejected_by_ver)));
    }
    sendUp(reply);
    return true;
}

bool
StateManager::onActivateClusterStateVersion(const std::shared_ptr<api::ActivateClusterStateVersionCommand>& cmd)
{
    auto reply = std::make_shared<api::ActivateClusterStateVersionReply>(*cmd);
    {
        std::lock_guard lock(_stateLock);
        reply->setActualVersion(_systemState->getVersion());
        if (cmd->version() == _systemState->getVersion()) {
            _reported_host_info_cluster_state_version = _systemState->getVersion();
        }
    }
    sendUp(reply);
    return true;
}

void
StateManager::run(framework::ThreadHandle& thread)
{
    while (true) {
        thread.registerTick(framework::UNKNOWN_CYCLE);
        if (thread.interrupted()) {
            break;
        }
        tick();
        std::unique_lock guard(_threadLock);
        if (!_requested_almost_immediate_node_state_replies.load(std::memory_order_relaxed)) {
            _threadCond.wait_for(guard, 1000ms);
        }
    }

}

void
StateManager::warn_on_missing_health_ping()
{
    vespalib::steady_time now(vespalib::steady_clock::now());
    std::optional<vespalib::steady_time> health_ping_time;
    {
        std::lock_guard lock(_stateLock);
        if (now <= _health_ping_warn_time) {
            return;
        }
        health_ping_time = _health_ping_time;
        _health_ping_warn_time = now + _health_ping_warn_interval;
    }
    if (health_ping_time.has_value()) {
        vespalib::duration duration = now - health_ping_time.value();
        LOG(warning, "Last health ping from cluster controller was %1.1f seconds ago", vespalib::to_s(duration));
    } else {
        vespalib::duration duration = now - _start_time;
        LOG(warning, "No health pings from cluster controller since startup %1.1f seconds ago", vespalib::to_s(duration));
    }
}

void
StateManager::tick() {
    bool almost_immediate_replies = _requested_almost_immediate_node_state_replies.load(std::memory_order_relaxed);
    if (almost_immediate_replies) {
        _requested_almost_immediate_node_state_replies.store(false, std::memory_order_relaxed);
        sendGetNodeStateReplies();
    } else {
        sendGetNodeStateReplies(_component.getClock().getMonotonicTime());
    }
    warn_on_missing_health_ping();
}

void
StateManager::set_require_strictly_increasing_cluster_state_versions(bool req) noexcept
{
    std::lock_guard guard(_stateLock);
    _require_strictly_increasing_cluster_state_versions = req;
}

bool
StateManager::sendGetNodeStateReplies() {
    return sendGetNodeStateReplies(0xffff);
}
bool
StateManager::sendGetNodeStateReplies(vespalib::steady_time olderThanTime) {
    return sendGetNodeStateReplies(olderThanTime, 0xffff);
}
bool
StateManager::sendGetNodeStateReplies(uint16_t nodeIndex) {
    return sendGetNodeStateReplies(vespalib::steady_time::max(), nodeIndex);
}
bool
StateManager::sendGetNodeStateReplies(vespalib::steady_time olderThanTime, uint16_t node)
{
    std::vector<std::shared_ptr<api::GetNodeStateReply>> replies;
    {
        std::unique_lock guard(_stateLock);
        for (auto it = _queuedStateRequests.begin(); it != _queuedStateRequests.end();) {
            if (node != 0xffff && node != it->second->getSourceIndex()) {
                ++it;
            } else if (it->first < olderThanTime) {
                LOG(debug, "Sending reply to msg with id %" PRIu64, it->second->getMsgId());

                replies.emplace_back(std::make_shared<api::GetNodeStateReply>(*it->second, *_nodeState));
                auto eraseIt = it++;
                mark_controller_as_having_observed_explicit_node_state(guard, eraseIt->second->getSourceIndex());
                _queuedStateRequests.erase(eraseIt);
            } else {
                ++it;
            }
        }
        if (replies.empty()) {
            return false;
        }
    }
    const std::string nodeInfo(getNodeInfo());
    for (auto& reply : replies) {
        reply->setNodeInfo(nodeInfo);
        sendUp(reply);
    }
    return true;
}

std::string
StateManager::getNodeInfo() const
{
    // Generate report from last to info
    vespalib::asciistream json;
    vespalib::JsonStream stream(json, true);
    stream << Object();
    { // Print metrics
        try {
            _nodeStateReporter.report(stream);
        } catch (vespalib::Exception& e) {
            stream << Object() << "error" << e.getMessage() << End();
        }
    }

    // Report cluster version. It would have been tricky to encapsulate this in
    // a HostReporter, because:
    // - That HostReporter would somehow need to get hold of the version
    //   from the cluster state from this StateManager.
    // - the public getSystemState() need (and should) grab a lock on
    //   _systemLock.
    // - getNodeInfo() (this function) always acquires the same lock.
    std::lock_guard guard(_stateLock);
    stream << "cluster-state-version" << _reported_host_info_cluster_state_version;

    _hostInfo->printReport(stream);
    stream << End();
    stream.finalize();

    return std::string(json.str());
}

void
StateManager::clear_controllers_observed_explicit_node_state_vector()
{
    std::lock_guard guard(_stateLock);
    // Next GetNodeState request from any controller will be replied to instantly
    _controllers_observed_explicit_node_state.clear();
}

void StateManager::immediately_send_get_node_state_replies() {
    LOG(debug, "Immediately replying to all pending GetNodeState requests");
    clear_controllers_observed_explicit_node_state_vector();
    sendGetNodeStateReplies();
}

void
StateManager::request_almost_immediate_node_state_replies()
{
    clear_controllers_observed_explicit_node_state_vector();
    std::unique_lock guard(_threadLock);
    _requested_almost_immediate_node_state_replies.store(true, std::memory_order_relaxed);
    _threadCond.notify_all();
}

} // storage
