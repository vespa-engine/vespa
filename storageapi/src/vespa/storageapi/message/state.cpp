// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state.h"
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <ostream>

namespace storage {
namespace api {

IMPLEMENT_COMMAND(GetNodeStateCommand, GetNodeStateReply)
IMPLEMENT_REPLY(GetNodeStateReply)
IMPLEMENT_COMMAND(SetSystemStateCommand, SetSystemStateReply)
IMPLEMENT_REPLY(SetSystemStateReply)

GetNodeStateCommand::GetNodeStateCommand(lib::NodeState::UP expectedState)
    : StorageCommand(MessageType::GETNODESTATE),
      _expectedState(std::move(expectedState))
{
}

void
GetNodeStateCommand::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "GetNodeStateCommand(";
    if (_expectedState.get() != 0) {
        out << "Expected state: " << *_expectedState;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

GetNodeStateReply::GetNodeStateReply(const GetNodeStateCommand& cmd)
    : StorageReply(cmd),
      _state()
{
}

GetNodeStateReply::GetNodeStateReply(const GetNodeStateCommand& cmd,
                                     const lib::NodeState& state)
    : StorageReply(cmd),
      _state(new lib::NodeState(state))
{
}

void
GetNodeStateReply::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "GetNodeStateReply(";
    if (_state.get()) {
        out << "State: " << *_state;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

SetSystemStateCommand::SetSystemStateCommand(const lib::ClusterStateBundle& state)
    : StorageCommand(MessageType::SETSYSTEMSTATE),
      _state(state)
{
}

SetSystemStateCommand::SetSystemStateCommand(const lib::ClusterState& state)
    : StorageCommand(MessageType::SETSYSTEMSTATE),
      _state(state)
{
}

void
SetSystemStateCommand::print(std::ostream& out, bool verbose,
                             const std::string& indent) const
{
    out << "SetSystemStateCommand(" << *_state.getBaselineClusterState() << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

SetSystemStateReply::SetSystemStateReply(const SetSystemStateCommand& cmd)
    : StorageReply(cmd),
      _state(cmd.getClusterStateBundle())
{
}

void
SetSystemStateReply::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "SetSystemStateReply()";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

} // api
} // storage
