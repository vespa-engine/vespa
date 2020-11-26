// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pending_gid_to_lid_changes.h"
#include "gid_to_lid_change_handler.h"

namespace proton {

PendingGidToLidChanges::PendingGidToLidChanges(GidToLidChangeHandler& handler, std::vector<PendingGidToLidChange> &&pending_changes)
    : IPendingGidToLidChanges(),
      _handler(handler),
      _pending_changes(std::move(pending_changes))
{
}

PendingGidToLidChanges::~PendingGidToLidChanges() = default;

void
PendingGidToLidChanges::notify_done()
{
    for (auto& change : _pending_changes) {
        if (change.is_remove()) {
            _handler.notifyRemoveDone(change.get_gid(), change.get_serial_num());
        } else {
            _handler.notifyPutDone(std::move(change).steal_context(), change.get_gid(), change.get_lid(), change.get_serial_num());
        }
    }
}

}
