// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/state/state.h>

#include <vespa/vespalib/util/exceptions.h>

namespace storage::lib {

const State State::UNKNOWN("Unknown", "-", 0, true,  true,  false, false, false);
const State State::MAINTENANCE("Maintenance", "m", 1, false, false, true,  true,  false);
const State State::DOWN("Down", "d", 2, false, false, true,  true,  true);
const State State::STOPPING("Stopping", "s", 3, true,  true,  false, false, true);
const State State::INITIALIZING("Initializing", "i", 4, true,  true,  false, false, true);
const State State::RETIRED("Retired", "r", 5, false, false, true, true,  false);
const State State::UP("Up", "u", 6, true,  true,  true,  true,  true);

const State&
State::get(vespalib::stringref serialized)
{
    if (serialized.size() == 1) switch(serialized[0]) {
        case '-': return UNKNOWN;
        case 'm': return MAINTENANCE;
        case 'd': return DOWN;
        case 's': return STOPPING;
        case 'i': return INITIALIZING;
        case 'r': return RETIRED;
        case 'u': return UP;
        default: break;
    }
    throw vespalib::IllegalArgumentException(
            "Unknown state " + serialized + " given.", VESPA_STRLOC);
}

State::State(vespalib::stringref name, vespalib::stringref serialized,
             uint8_t rank,
             bool validDistributorReported, bool validStorageReported,
             bool validDistributorWanted, bool validStorageWanted,
             bool validCluster)
    : _name(name),
      _serialized(serialized),
      _rankValue(rank),
      _validReportedNodeState(2),
      _validWantedNodeState(2),
      _validClusterState(validCluster)
{
        // Since this is static initialization and NodeType is not necessarily
        // created yet, use these enum values.
    uint32_t storage = 0;
    uint32_t distributor = 1;
    _validReportedNodeState[storage] = validStorageReported;
    _validReportedNodeState[distributor] = validDistributorReported;
    _validWantedNodeState[storage] = validStorageWanted;
    _validWantedNodeState[distributor] = validDistributorWanted;
}

State::~State() = default;

void
State::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) indent;
    out << (verbose ? _name : _serialized);
}

}
