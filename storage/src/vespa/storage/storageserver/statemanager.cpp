// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statemanager.h"
#include <vespa/defaults.h>
#include <fstream>
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storage/storageserver/storagemetricsset.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/storage/common/cluster_state_bundle.h>
#include <sys/types.h>
#include <unistd.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".state.manager");

namespace storage {

StateManager::StateManager(StorageComponentRegister& compReg,
                           metrics::MetricManager& metricManager,
                           std::unique_ptr<HostInfo> hostInfo,
                           bool testMode)
    : StorageLink("State manager"),
      framework::HtmlStatusReporter("systemstate", "Node and system state"),
      _noThreadTestMode(testMode),
      _component(compReg, "statemanager"),
      _metricManager(metricManager),
      _stateLock(),
      _listenerLock(),
      _grabbedExternalLock(false),
      _notifyingListeners(false),
      _nodeState(new lib::NodeState(_component.getNodeType(), lib::State::INITIALIZING)),
      _nextNodeState(),
      _systemState(std::make_shared<const ClusterStateBundle>(lib::ClusterState())),
      _nextSystemState(),
      _stateListeners(),
      _queuedStateRequests(),
      _threadMonitor(),
      _lastProgressUpdateCausingSend(0),
      _progressLastInitStateSend(-1),
      _systemStateHistory(),
      _systemStateHistorySize(50),
      _hostInfo(std::move(hostInfo))
{
    _nodeState->setMinUsedBits(58);
    _nodeState->setStartTimestamp(
            _component.getClock().getTimeInSeconds().getTime());
    _component.registerStatusPage(*this);
}

StateManager::~StateManager()
{
    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());
    if (_thread.get() != 0) {
        LOG(debug, "onClose() not called before destructor");
        _thread->interruptAndJoin(&_threadMonitor);
    }
}

void
StateManager::onOpen()
{
    framework::MilliSecTime maxProcessingTime(30 * 1000);
    if (!_noThreadTestMode) {
        _thread = _component.startThread(*this, maxProcessingTime);
    }
}

void
StateManager::onClose()
{
    if (_thread.get() != 0) {
        _thread->interruptAndJoin(&_threadMonitor);
        _thread.reset(0);
    }
    sendGetNodeStateReplies();
}

void
StateManager::print(std::ostream& out, bool verbose,
              const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "StateManager()";
}

#ifdef ENABLE_BUCKET_OPERATION_LOGGING
namespace {

vespalib::string
escapeHtml(vespalib::stringref str)
{
    vespalib::asciistream ss;
    for (size_t i = 0; i < str.size(); ++i) {
        switch (str[i]) {
        case '<':
            ss << "&lt;";
            break;
        case '>':
            ss << "&gt;";
            break;
        case '&':
            ss << "&amp;";
            break;
        default:
            ss << str[i];
        }
    }
    return ss.str();
}

}
#endif

void
StateManager::reportHtmlStatus(std::ostream& out,
                               const framework::HttpUrlPath& path) const
{
    (void) path;
#ifdef ENABLE_BUCKET_OPERATION_LOGGING
    if (path.hasAttribute("history")) {
        std::istringstream iss(path.getAttribute("history"), std::istringstream::in);
        uint64_t rawId;
        iss >> std::hex >> rawId;
        document::BucketId bid(rawId);
        out << "<h3>History for " << bid << "</h3>\n";
        vespalib::string history(
                debug::BucketOperationLogger::getInstance().getHistory(bid));
        out << "<pre>" << escapeHtml(history) << "</pre>\n";
        return;
    } else if (path.hasAttribute("search")) {
        vespalib::string substr(path.getAttribute("search"));
        out << debug::BucketOperationLogger::getInstance()
            .searchBucketHistories(substr, "/systemstate?history=");
        return;
    }
#endif

    {
        vespalib::LockGuard lock(_stateLock);
        const auto &baseLineClusterState = _systemState->getBaselineClusterState();
        out << "<h1>Current system state</h1>\n"
            << "<code>" << baseLineClusterState->toString(true) << "</code>\n"
            << "<h1>Current node state</h1>\n"
            << "<code>" << baseLineClusterState->getNodeState(lib::Node(
                        _component.getNodeType(), _component.getIndex())
                                                     ).toString(true)
            << "</code>\n"
            << "<h1>Reported node state</h1>\n"
            << "<code>" << _nodeState->toString(true) << "</code>\n"
            << "<h1>Pending state requests</h1>\n"
            << _queuedStateRequests.size() << "\n"
            << "<h1>System state history</h1>\n"
            << "<table border=\"1\"><tr>"
            << "<th>Received at time</th><th>State</th></tr>\n";
        for (std::deque<TimeSysStatePair>::const_reverse_iterator it
                = _systemStateHistory.rbegin();
             it != _systemStateHistory.rend(); ++it)
        {
            out << "<tr><td>" << it->first << "</td><td>"
                << *it->second->getBaselineClusterState() << "</td></tr>\n";
        }
        out << "</table>\n";
    }
}

lib::Node
StateManager::thisNode() const
{
    return lib::Node(_component.getNodeType(), _component.getIndex());
}

lib::NodeState::CSP
StateManager::getReportedNodeState() const
{
    vespalib::LockGuard lock(_stateLock);
    return _nodeState;
}

lib::NodeState::CSP
StateManager::getCurrentNodeState() const
{
    vespalib::LockGuard lock(_stateLock);
    return std::make_shared<const lib::NodeState>
        (_systemState->getBaselineClusterState()->getNodeState(thisNode()));
}

std::shared_ptr<const ClusterStateBundle>
StateManager::getClusterStateBundle() const
{
    vespalib::LockGuard lock(_stateLock);
    return _systemState;
}

void
StateManager::addStateListener(StateListener& listener)
{
    vespalib::LockGuard lock(_listenerLock);
    _stateListeners.push_back(&listener);
}

void
StateManager::removeStateListener(StateListener& listener)
{
    vespalib::LockGuard lock(_listenerLock);
    for (std::list<StateListener*>::iterator it = _stateListeners.begin();
         it != _stateListeners.end();)
    {
        if (*it == &listener) {
            it = _stateListeners.erase(it);
        } else {
            ++it;
        }
    }
}

struct StateManager::ExternalStateLock : public NodeStateUpdater::Lock {
    StateManager& _manager;

    ExternalStateLock(StateManager& manager) : _manager(manager) {}
    ~ExternalStateLock() {
        {
            vespalib::MonitorGuard lock(_manager._stateLock);
            _manager._grabbedExternalLock = false;
            lock.broadcast();
        }
        _manager.notifyStateListeners();
    }
};

NodeStateUpdater::Lock::SP
StateManager::grabStateChangeLock()
{
    vespalib::MonitorGuard lock(_stateLock);
    while (_grabbedExternalLock || _nextNodeState.get()) {
        lock.wait();
    }
    _grabbedExternalLock = true;
    return Lock::SP(new ExternalStateLock(*this));
}

void
StateManager::setReportedNodeState(const lib::NodeState& state)
{
    vespalib::LockGuard lock(_stateLock);
    if (!_grabbedExternalLock) {
        LOG(error,
            "Cannot set reported node state without first having "
            "grabbed external lock");
        assert(false);
    }
    if (_nodeState->getDiskCount() != 0 &&
        state.getDiskCount() != _nodeState->getDiskCount())
    {
        std::ostringstream ost;
        ost << "Illegal to alter disk count after initialization. Tried to "
            << "alter disk count from " << _nodeState->getDiskCount()
            << " to " << state.getDiskCount();
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    LOG(debug, "Adjusting reported node state to %s -> %s",
        _nodeState->toString().c_str(), state.toString().c_str());
    _nextNodeState.reset(new lib::NodeState(state));
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
    if (_notifyingListeners) return;
    vespalib::LockGuard listenerLock(_listenerLock);
    _notifyingListeners = true;
    lib::NodeState::SP newState;
    while (true) {
        {
            vespalib::MonitorGuard stateLock(_stateLock);
            if (_nextNodeState.get() == 0 && _nextSystemState.get() == 0) {
                _notifyingListeners = false;
                stateLock.broadcast();
                break; // No change
            }
            if (_nextNodeState.get() != 0) {
                assert(!(_nodeState->getState() == State::UP
                         && _nextNodeState->getState() == State::INITIALIZING));

                if (_nodeState->getState() == State::INITIALIZING
                    && _nextNodeState->getState() == State::INITIALIZING
                    && _component.getClock().getTimeInMillis()
                            - _lastProgressUpdateCausingSend
                        < framework::MilliSecTime(1000)
                    && _nextNodeState->getInitProgress() < 1
                    && _nextNodeState->getInitProgress()
                            - _progressLastInitStateSend < 0.01)
                {
                    // For this special case, where we only have gotten a little
                    // initialization progress and we have reported recently,
                    // don't trigger sending get node state reply yet.
                } else {
                    newState = _nextNodeState;
                    if (!_queuedStateRequests.empty()
                        && _nextNodeState->getState() == State::INITIALIZING)
                    {
                        _lastProgressUpdateCausingSend
                                = _component.getClock().getTimeInMillis();
                        _progressLastInitStateSend
                                = newState->getInitProgress();
                    } else {
                        _lastProgressUpdateCausingSend
                                = framework::MilliSecTime(0);
                        _progressLastInitStateSend = -1;
                    }
                }
                _nodeState = _nextNodeState;
                _nextNodeState.reset();
            }
            if (_nextSystemState.get() != 0) {
                enableNextClusterState();
            }
            stateLock.broadcast();
        }
        for (std::list<StateListener*>::iterator it = _stateListeners.begin();
             it != _stateListeners.end(); ++it)
        {
            (**it).handleNewState();
                // If one of them actually altered the state again, abort
                // sending events, update states and send new one to all.
            if (_nextNodeState.get() != 0 || _nextSystemState.get() != 0) break;
        }
    }
    if (newState.get() != 0) sendGetNodeStateReplies();
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
    _nextSystemState.reset();
    _systemStateHistory.push_back(TimeSysStatePair(
                _component.getClock().getTimeInMillis(), _systemState));
}

void
StateManager::logNodeClusterStateTransition(
        const ClusterStateBundle& currentState,
        const ClusterStateBundle& newState) const
{
    lib::Node self(thisNode());
    const lib::State& before(currentState.getBaselineClusterState()->getNodeState(self).getState());
    const lib::State& after(newState.getBaselineClusterState()->getNodeState(self).getState());
    if (before != after) {
        LOG(info, "Transitioning from state '%s' to '%s' "
                  "(cluster state version %u)",
            before.getName().c_str(),
            after.getName().c_str(),
            newState.getVersion());
    }
}

bool
StateManager::onGetNodeState(const api::GetNodeStateCommand::SP& cmd)
{
    bool sentReply = false;
    if (cmd->getSourceIndex() != 0xffff) {
        sentReply = sendGetNodeStateReplies(framework::MilliSecTime(0),
                                            cmd->getSourceIndex());
    }
    std::shared_ptr<api::GetNodeStateReply> reply;
    {
        vespalib::LockGuard lock(_stateLock);
        if (cmd->getExpectedState() != 0
            && (*cmd->getExpectedState() == *_nodeState || sentReply))
        {
            LOG(debug, "Received get node state request with timeout of "
                       "%u milliseconds. Scheduling to be answered in "
                       "%u milliseconds unless a node state change "
                       "happens before that time.",
                cmd->getTimeout(), cmd->getTimeout() * 800 / 1000);
            TimeStatePair pair(
                    _component.getClock().getTimeInMillis()
                    + framework::MilliSecTime(cmd->getTimeout() * 800 / 1000),
                    cmd);
            _queuedStateRequests.push_back(pair);
        } else {
            LOG(debug, "Answered get node state request right away since it "
                       "thought we were in nodestate %s, while our actual "
                       "node state is currently %s and we didn't just reply to "
                       "existing request.",
                cmd->getExpectedState() == 0 ? "unknown"
                        : cmd->getExpectedState()->toString().c_str(),
                _nodeState->toString().c_str());
            reply.reset(new api::GetNodeStateReply(*cmd, *_nodeState));
            lock.unlock();
            std::string nodeInfo(getNodeInfo());
            reply->setNodeInfo(nodeInfo);
        }
    }
    if (reply.get()) {
        sendUp(reply);
    }
    return true;
}

void
StateManager::setClusterState(const lib::ClusterState& c)
{
    {
        vespalib::LockGuard lock(_stateLock);
        _nextSystemState = std::make_shared<const ClusterStateBundle>(c);
    }
    notifyStateListeners();
}

bool
StateManager::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    setClusterState(cmd->getSystemState());
    std::shared_ptr<api::SetSystemStateReply> reply(
            new api::SetSystemStateReply(*cmd));
    sendUp(reply);
    return true;
}

void
StateManager::run(framework::ThreadHandle& thread)
{
    while (true) {
        thread.registerTick();
        vespalib::MonitorGuard guard(_threadMonitor);
            // Take lock before doing stuff, to be sure we don't wait after
            // destructor have grabbed lock to stop() us.
        if (thread.interrupted()) break;
        tick();
        guard.wait(1000);
    }

}

void
StateManager::tick() {
    framework::MilliSecTime time(_component.getClock().getTimeInMillis());
    sendGetNodeStateReplies(time);
}

bool
StateManager::sendGetNodeStateReplies(framework::MilliSecTime olderThanTime,
                                      uint16_t node)
{
    std::list<std::shared_ptr<api::GetNodeStateReply> > replies;
    {
        vespalib::MonitorGuard guard(_stateLock);
        for (std::list<TimeStatePair>::iterator it
                = _queuedStateRequests.begin();
             it != _queuedStateRequests.end();)
        {
            if (node != 0xffff && node != it->second->getSourceIndex()) {
                ++it;
            } else if (!olderThanTime.isSet() || it->first < olderThanTime) {
                LOG(debug, "Sending reply to msg with id %lu",
                    it->second->getMsgId());

                std::shared_ptr<api::GetNodeStateReply> reply(
                        new api::GetNodeStateReply(*it->second, *_nodeState));
                replies.push_back(reply);
                std::list<TimeStatePair>::iterator eraseIt = it++;
                _queuedStateRequests.erase(eraseIt);
            } else {
                ++it;
            }
        }
        if (replies.empty()) return false;
    }
    std::string nodeInfo(getNodeInfo());
    for (std::list<std::shared_ptr<api::GetNodeStateReply> >::iterator it
            = replies.begin(); it != replies.end(); ++it)
    {
        (**it).setNodeInfo(nodeInfo);
        sendUp(*it);
    }
    return true;
}

namespace {
    std::string getHostInfoFilename(bool advanceCount) {
        static uint32_t fileCounter = 0;
        static pid_t pid = getpid();
        if (advanceCount) ++fileCounter;
        uint32_t fileIndex = fileCounter % 8;
        std::ostringstream fileName;
        fileName << vespa::Defaults::underVespaHome("tmp/hostinfo")
                 << "." << pid << "." << fileIndex << ".report";
        return fileName.str();
    }
}

std::string
StateManager::getNodeInfo() const
{
    // Generate report from last to info
    vespalib::asciistream json;
    vespalib::JsonStream stream(json, true);
    stream << Object();
    { // Print metrics
        stream << "metrics";
        try {
            metrics::MetricLockGuard lock(_metricManager.getMetricLock());
            std::vector<uint32_t> periods(
                    _metricManager.getSnapshotPeriods(lock));
            if (periods.size() > 0) {
                uint32_t period = periods[0];
                const metrics::MetricSnapshot& snapshot(
                        _metricManager.getMetricSnapshot(lock, period));
                metrics::JsonWriter metricJsonWriter(stream);
                _metricManager.visit(lock,  snapshot, metricJsonWriter, "fleetcontroller");
            } else {
                stream << Object() << "error" << "no snapshot periods" << End();
            }
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
    vespalib::MonitorGuard guard(_stateLock);
    stream << "cluster-state-version" << _systemState->getVersion();

    _hostInfo->printReport(stream);
    stream << End();
    stream.finalize();
        // Add deadlock detector data.
    //ost << "Deadlock detector data from "
    //    << _component.getClock().getTimeInSeconds().toString() << "\n\n";
    //framework::HttpUrlPath path("");
    //_storageServer.getDeadLockDetector().getStatus(ost, path);
        // Dump report to new report file.
    std::string oldFile(getHostInfoFilename(false));
    std::string newFile(getHostInfoFilename(true));
    std::ofstream of(newFile.c_str());
    of << json.str();
    of.close();
        // If dumping went ok, delete old report file
    vespalib::unlink(oldFile);
        // Return report
    return json.str();
}

} // storage
