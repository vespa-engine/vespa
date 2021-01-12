// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::State
 *
 * Defines legal states for various uses. Split this into its own class such
 * that we can easily see what states are legal to use in what situations.
 * They double as node states nodes report they are in, and
 * wanted states set external sources.
 */
#pragma once

#include "nodetype.h"
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace storage::lib {

class State : public vespalib::Printable {
    vespalib::string _name;
    vespalib::string _serialized;
    uint8_t _rankValue;
    std::vector<bool> _validReportedNodeState;
    std::vector<bool> _validWantedNodeState;
    bool _validClusterState;

    State(const State&);
    State(vespalib::stringref name, vespalib::stringref serialized,
          uint8_t rank,
          bool validDistributorReported, bool validStorageReported,
          bool validDistributorWanted, bool validStorageWanted,
          bool validCluster);
    ~State();

    State& operator=(const State&);

public:
    static const State UNKNOWN;
    static const State MAINTENANCE;
    static const State DOWN;
    static const State STOPPING;
    static const State INITIALIZING;
    static const State RETIRED;
    static const State UP;

    /** Throws vespalib::IllegalArgumentException if invalid state given. */
    static const State& get(vespalib::stringref serialized);
    const vespalib::string& serialize() const { return _serialized; }

    bool validReportedNodeState(const NodeType& node) const { return _validReportedNodeState[node]; }
    bool validWantedNodeState(const NodeType& node) const { return _validWantedNodeState[node]; }
    bool validClusterState() const { return _validClusterState; }

    bool maySetWantedStateForThisNodeState(const State& wantedState) const {
        return (wantedState._rankValue <= _rankValue);
    }

    /**
     * Get a string that represents a more human readable version of
     * the state than what can be provided through the single-character
     * serialized representation.
     *
     * Example: State::RETIRED.getName() -> "Retired"
     */
    const vespalib::string& getName() const noexcept {
        return _name;
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool operator==(const State& other) const { return (&other == this); }
    bool operator!=(const State& other) const { return (&other != this); }

    /**
     * Utility function to check whether this state is one of the given
     * states, given as the single character they are serialized as.
     * For instance, "um" will check if this state is up or maintenance.
     */
    bool oneOf(const char* states) const {
        for (const char* c = states; *c != '\0'; ++c) {
            if (*c == _serialized[0]) return true;
        }
        return false;
    }
};

}
