// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>

namespace storage::api {

/**
 * @class GetNodeStateCommand
 * @ingroup message
 *
 * @brief Command for setting node state. No payload
 */
class GetNodeStateCommand : public StorageCommand {
    lib::NodeState::UP _expectedState;

public:
    explicit GetNodeStateCommand(lib::NodeState::UP expectedState);

    const lib::NodeState* getExpectedState() const { return _expectedState.get(); }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(GetNodeStateCommand, onGetNodeState)
};

/**
 * @class GetNodeStateReply
 * @ingroup message
 *
 * @brief Reply to GetNodeStateCommand
 */
class GetNodeStateReply : public StorageReply {
    lib::NodeState::UP _state;
    std::string _nodeInfo;

public:
    GetNodeStateReply(const GetNodeStateCommand&); // Only used on makeReply()
    GetNodeStateReply(const GetNodeStateCommand&, const lib::NodeState&);

    bool hasNodeState() const { return (_state.get() != 0); }
    const lib::NodeState& getNodeState() const { return *_state; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void setNodeInfo(const std::string& info) { _nodeInfo = info; }
    const std::string& getNodeInfo() const { return _nodeInfo; }

    DECLARE_STORAGEREPLY(GetNodeStateReply, onGetNodeStateReply)
};

/**
 * @class SetSystemStateCommand
 * @ingroup message
 *
 * @brief Command for telling a node about the system state - state of each node
 *  in the system and state of the system (all ok, no merging, block
 *  put/get/remove etx)
 */
class SetSystemStateCommand : public StorageCommand {
    lib::ClusterStateBundle _state;

public:
    explicit SetSystemStateCommand(const lib::ClusterStateBundle &state);
    explicit SetSystemStateCommand(const lib::ClusterState &state);
    const lib::ClusterState& getSystemState() const { return *_state.getBaselineClusterState(); }
    const lib::ClusterStateBundle& getClusterStateBundle() const { return _state; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(SetSystemStateCommand, onSetSystemState)
};

/**
 * @class SetSystemStateReply
 * @ingroup message
 *
 * @brief Reply received after a SetSystemStateCommand.
 */
class SetSystemStateReply : public StorageReply {
    lib::ClusterStateBundle _state;

public:
    explicit SetSystemStateReply(const SetSystemStateCommand& cmd);

    // Not serialized. Available locally
    const lib::ClusterState& getSystemState() const { return *_state.getBaselineClusterState(); }
    const lib::ClusterStateBundle& getClusterStateBundle() const { return _state; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(SetSystemStateReply, onSetSystemStateReply)
};

}
