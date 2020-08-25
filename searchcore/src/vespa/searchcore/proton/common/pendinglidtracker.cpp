// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pendinglidtracker.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <algorithm>
#include <cassert>

namespace proton {

IPendingLidTracker::Token::Token()
    : _tracker(nullptr),
      _lid(0u)
{}
IPendingLidTracker::Token::Token(uint32_t lid, IPendingLidTracker &tracker)
    : _tracker(&tracker),
      _lid(lid)
{}

IPendingLidTracker::Token::~Token() {
    if (_tracker != nullptr) {
        _tracker->consume(_lid);
    }
}

bool
ILidCommitState::needCommit(uint32_t lid) {
    return waitState(State::NEED_COMMIT, lid) == State::NEED_COMMIT;
}
bool
ILidCommitState::needCommit(const std::vector<uint32_t> & lids) {
    return waitState(State::NEED_COMMIT, lids) == State::NEED_COMMIT;
}
bool
ILidCommitState::needCommit() {
    return waitState(State::NEED_COMMIT) == State::NEED_COMMIT;
}

void
ILidCommitState::waitComplete(uint32_t lid) {
    waitState(State::COMPLETE, lid);
}
void
ILidCommitState::waitComplete(const std::vector<uint32_t> & lids) {
    waitState(State::COMPLETE, lids);
}
void
ILidCommitState::waitComplete() {
    waitState(State::COMPLETE);
}

PendingLidTracker::PendingLidTracker()
    : _mutex(),
      _cond(),
      _pending()
{}

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
PendingLidTracker::waitFor(MonitorGuard & guard, State, uint32_t lid) {
    while (_pending.find(lid) != _pending.end()) {
        _cond.wait(guard);
    }
    return State::COMPLETE;
}

ILidCommitState::State
PendingLidTracker::waitState(State) {
    MonitorGuard guard(_mutex);
    while ( ! _pending.empty() ) {
        _cond.wait(guard);
    }
    return State::COMPLETE;
}

ILidCommitState::State
PendingLidTracker::waitState(State state, uint32_t lid) {
    MonitorGuard guard(_mutex);
    return waitFor(guard, state, lid);
}

ILidCommitState::State
PendingLidTracker::waitState(State state, const std::vector<uint32_t> & lids) {
    MonitorGuard guard(_mutex);
    for (uint32_t lid : lids) {
        if ((waitFor(guard, state, lid) == state) && (state == State::NEED_COMMIT)) {
            return State::NEED_COMMIT;
        }
    }
    return State::COMPLETE;
}

PendingLidTrackerBase::Snapshot
PendingLidTracker::produceSnapshot() {
    return Snapshot();
}

TwoPhasePendingLidTracker::TwoPhasePendingLidTracker()
    : _mutex(),
      _cond(),
      _pending()
{}

TwoPhasePendingLidTracker::~TwoPhasePendingLidTracker() {
    assert(_pending.empty());
}

IPendingLidTracker::Token
TwoPhasePendingLidTracker::produce(uint32_t lid) {
    std::lock_guard guard(_mutex);
    _pending[lid].inflight_feed++;
    return Token(lid, *this);
}
void
TwoPhasePendingLidTracker::consume(uint32_t lid) {
    std::lock_guard guard(_mutex);
    auto found = _pending.find(lid);
    assert (found != _pending.end());
    assert (found->second.inflight_feed > 0);
    found->second.inflight_feed--;
    found->second.need_commit = true;
}

ILidCommitState::State
TwoPhasePendingLidTracker::waitFor(MonitorGuard & guard, State state, uint32_t lid) {
    for (auto found = _pending.find(lid); found != _pending.end(); found = _pending.find(lid)) {
        if ((state == State::NEED_COMMIT) && ((found->second.inflight_feed > 0) || found->second.need_commit)) {
            return State::NEED_COMMIT;
        }
        _cond.wait(guard);
    }
    return State::COMPLETE;
}

ILidCommitState::State
TwoPhasePendingLidTracker::waitState(State state) {
    MonitorGuard guard(_mutex);
    while ( ! _pending.empty() ) {
        for (const auto & entry : _pending) {
            if ((waitFor(guard, state, entry.first) == state) && (state == State::NEED_COMMIT)) {
                return State::NEED_COMMIT;
            }
        }
    }
    return State::COMPLETE;
}

ILidCommitState::State
TwoPhasePendingLidTracker::waitState(State state, uint32_t lid) {
    MonitorGuard guard(_mutex);
    return waitFor(guard, state, lid);
}

ILidCommitState::State
TwoPhasePendingLidTracker::waitState(State state, const std::vector<uint32_t> & lids) {
    MonitorGuard guard(_mutex);
    for (uint32_t lid : lids) {
        if ((waitFor(guard, state, lid) == state) && (state == State::NEED_COMMIT)) {
            return State::NEED_COMMIT;
        }
    }
    return State::COMPLETE;
}

PendingLidTrackerBase::Snapshot
TwoPhasePendingLidTracker::produceSnapshot() {
    List toCommit;
    MonitorGuard guard(_mutex);
    for (auto & entry : _pending) {
        if (entry.second.need_commit) {
            toCommit.emplace_back(entry.first);
            entry.second.inflight_commit ++;
            entry.second.need_commit = false;
        }
    }
    return std::make_unique<CommitList>(std::move(toCommit), *this);
}

void
TwoPhasePendingLidTracker::consumeSnapshot(List committed) {
    MonitorGuard guard(_mutex);
    for (const auto & lid : committed) {
        auto found = _pending.find(lid);
        assert(found != _pending.end());
        assert(found->second.inflight_commit >= 1);
        found->second.inflight_commit --;
        if (found->second.empty()) {
            _pending.erase(found);
        }
    }
    _cond.notify_all();
}

TwoPhasePendingLidTracker::CommitList::CommitList(List lids, TwoPhasePendingLidTracker & tracker)
    : _tracker(&tracker),
      _lids(std::move(lids))
{ }
TwoPhasePendingLidTracker::CommitList::CommitList(CommitList && rhs) noexcept
    : _tracker(rhs._tracker),
      _lids(std::move(rhs._lids))
{
    rhs._tracker = nullptr;
}
TwoPhasePendingLidTracker::CommitList::~CommitList() {
    if (_tracker != nullptr) {
        _tracker->consumeSnapshot(std::move(_lids));
    }
}


}
