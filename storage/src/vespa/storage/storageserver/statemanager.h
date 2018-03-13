// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::StateManager
 * @ingroup storageserver
 *
 * @brief Keeps and updates node and system states.
 *
 * This component implements the NodeStateUpdater interface to handle states
 * for all components. See that interface for documentation.
 *
 * In addition, this manager is a storage link such that it can handle the
 * various commands for setting and retrieving states.
 */
#pragma once

#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <deque>
#include <map>
#include <list>
#include <atomic>

namespace metrics {
    class MetricManager;
}

namespace storage {

namespace lib { class ClusterStateBundle; }

class StateManager : public NodeStateUpdater,
                     public StorageLink,
                     public framework::HtmlStatusReporter,
                     private framework::Runnable,
                     private vespalib::JsonStreamTypes
{
    bool _noThreadTestMode;
    StorageComponent _component;
    metrics::MetricManager& _metricManager;
    vespalib::Monitor _stateLock;
    vespalib::Lock _listenerLock;
    bool _grabbedExternalLock;
    std::atomic<bool> _notifyingListeners;
    std::shared_ptr<lib::NodeState> _nodeState;
    std::shared_ptr<lib::NodeState> _nextNodeState;
    using ClusterStateBundle = lib::ClusterStateBundle;
    std::shared_ptr<const ClusterStateBundle> _systemState;
    std::shared_ptr<const ClusterStateBundle> _nextSystemState;
    std::list<StateListener*> _stateListeners;
    typedef std::pair<framework::MilliSecTime,
                      api::GetNodeStateCommand::SP> TimeStatePair;
    std::list<TimeStatePair> _queuedStateRequests;
    mutable vespalib::Monitor _threadMonitor;
    framework::MilliSecTime _lastProgressUpdateCausingSend;
    vespalib::Double _progressLastInitStateSend;
    using TimeSysStatePair = std::pair<framework::MilliSecTime, std::shared_ptr<const ClusterStateBundle>>;
    std::deque<TimeSysStatePair> _systemStateHistory;
    uint32_t _systemStateHistorySize;
    std::unique_ptr<HostInfo> _hostInfo;
    framework::Thread::UP _thread;

public:
    explicit StateManager(StorageComponentRegister&, metrics::MetricManager&,
                          std::unique_ptr<HostInfo>, bool testMode = false);
    ~StateManager();

    void onOpen() override;
    void onClose() override;

    void tick();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    lib::NodeState::CSP getReportedNodeState() const override;
    lib::NodeState::CSP getCurrentNodeState() const override;
    std::shared_ptr<const ClusterStateBundle> getClusterStateBundle() const override;

    void addStateListener(StateListener&) override;
    void removeStateListener(StateListener&) override;

    Lock::SP grabStateChangeLock() override;
    void setReportedNodeState(const lib::NodeState& state) override;
    void setClusterStateBundle(const ClusterStateBundle& c);
    HostInfo& getHostInfo() { return *_hostInfo; }

private:
    class ExternalStateLock;
    friend class ExternalStateLock;
    friend class StateManagerTest;

    void notifyStateListeners();
    bool sendGetNodeStateReplies(
            framework::MilliSecTime olderThanTime = framework::MilliSecTime(0),
            uint16_t index = 0xffff);

    lib::Node thisNode() const;

    /**
     * Overwrite the current cluster state with the one that is currently
     * pending.
     *
     * Appends the pending cluster state to a circular buffer of historic
     * states.
     *
     * Preconditions:
     *   - _stateLock is held
     *   - _systemState.get() != nullptr
     *   - _nextSystemState.get() != nullptr
     * Postconditions:
     *   - _systemState = old(_nextSystemState)
     *   - _nextSystemState.get() == nullptr
     */
    void enableNextClusterState();

    /**
     * Log this node's state transition as given by the cluster state iff the
     * state differs between currentState and newState.
     */
    void logNodeClusterStateTransition(
            const ClusterStateBundle& currentState,
            const ClusterStateBundle& newState) const;

    bool onGetNodeState(const std::shared_ptr<api::GetNodeStateCommand>&) override;
    bool onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>&) override;

    /**
     * _stateLock MUST NOT be held while calling.
     */
    std::string getNodeInfo() const;

    void run(framework::ThreadHandle&) override;
};

} // storage
