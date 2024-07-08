// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/state/state.h>
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>

namespace storage::lib {

const State&
State::get(std::string_view serialized)
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

State::State(std::string_view name, std::string_view serialized,
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
