// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "globals.h"

namespace storage::lib {

const State State::UNKNOWN("Unknown", "-", 0, true,  true,  false, false, false);
const State State::MAINTENANCE("Maintenance", "m", 1, false, false, true,  true,  false);
const State State::DOWN("Down", "d", 2, false, false, true,  true,  true);
const State State::STOPPING("Stopping", "s", 3, true,  true,  false, false, true);
const State State::INITIALIZING("Initializing", "i", 4, true,  true,  false, false, true);
const State State::RETIRED("Retired", "r", 5, false, false, true, true,  false);
const State State::UP("Up", "u", 6, true,  true,  true,  true,  true);

}

namespace storage::lib::clusterstate {

NodeState _G_defaultSDState(NodeType::STORAGE, State::DOWN);
NodeState _G_defaultDDState(NodeType::DISTRIBUTOR, State::DOWN);
NodeState _G_defaultSUState(NodeType::STORAGE, State::UP);
NodeState _G_defaultDUState(NodeType::DISTRIBUTOR, State::UP);

}
