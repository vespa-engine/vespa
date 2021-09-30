// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pendinglidtracker.h"
#include <cassert>

namespace proton {

PendingLidTrackerBase::PendingLidTrackerBase() = default;
PendingLidTrackerBase::~PendingLidTrackerBase() = default;

ILidCommitState::State
PendingLidTrackerBase::waitState(State state, uint32_t lid) const {
    MonitorGuard guard(_mutex);
    return waitFor(guard, state, lid);
}

ILidCommitState::State
PendingLidTrackerBase::waitState(State state, const LidList & lids) const {
    MonitorGuard guard(_mutex);
    State lowest = State::COMPLETED;
    for (uint32_t lid : lids) {
        State next = waitFor(guard, state, lid);
        if ((state == State::NEED_COMMIT) && next == state) {
            return next;
        }
        lowest = std::min(next, lowest);
    }
    return lowest;
}

PendingLidTracker::PendingLidTracker() = default;

PendingLidTracker::~PendingLidTracker() {
    assert(_pending.empty());
}

IPendingLidTracker::Token
PendingLidTracker::produce(uint32_t lid) {
    std::lock_guard guard(_mutex);
    _pending[lid]++;
    return Token(lid, *this);
}
void
PendingLidTracker::consume(uint32_t lid) {
    std::lock_guard guard(_mutex);
    auto found = _pending.find(lid);
    assert (found != _pending.end());
    assert (found->second > 0);
    if (found->second == 1) {
        _pending.erase(found);
        _cond.notify_all();
    } else {
        found->second--;
    }
}

ILidCommitState::State
PendingLidTracker::waitFor(MonitorGuard & guard, State state, uint32_t lid) const {
    for (auto found = _pending.find(lid); found != _pending.end(); found = _pending.find(lid)) {
        if (state == State::NEED_COMMIT) {
            return State::WAITING;
        }
        _cond.wait(guard);
    }
    return State::COMPLETED;
}

PendingLidTrackerBase::Snapshot
PendingLidTracker::produceSnapshot() {
    return Snapshot();
}

}
