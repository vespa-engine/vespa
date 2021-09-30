// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_pending_gid_to_lid_changes.h"
#include "pending_gid_to_lid_change.h"
#include <vector>

namespace proton {

class GidToLidChangeHandler;

/*
 * Class for a vector of gid to lid changes awaiting a force commit.
 */
class PendingGidToLidChanges : public IPendingGidToLidChanges
{
    GidToLidChangeHandler&             _handler;
    std::vector<PendingGidToLidChange> _pending_changes;
public:
    PendingGidToLidChanges(GidToLidChangeHandler& handler, std::vector<PendingGidToLidChange> &&pending_changes);
    ~PendingGidToLidChanges() override;
    void notify_done() override;
};

}
