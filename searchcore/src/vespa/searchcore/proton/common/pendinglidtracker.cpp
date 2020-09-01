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

void
ILidCommitState::waitComplete(uint32_t lid) const {
    waitState(State::COMPLETED, lid);
}
void
ILidCommitState::waitComplete(const LidList & lids) const {
    waitState(State::COMPLETED, lids);
}
void
ILidCommitState::waitComplete() const {
    waitState(State::COMPLETED);
}

PendingLidTrackerBase::PendingLidTrackerBase() = default;
PendingLidTrackerBase::~PendingLidTrackerBase() = default;

ILidCommitState::State
PendingLidTrackerBase::waitState(State state) const {
    auto pending = pendingLids();
    return waitState(state, pending);
}

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

ILidCommitState::LidList
PendingLidTracker::pendingLids() const {
    MonitorGuard guard(_mutex);
    LidList lids;
    lids.reserve(_pending.size());
    for (const auto & entry : _pending) {
        lids.push_back(entry.first);
    }
    return lids;
}

TwoPhasePendingLidTracker::TwoPhasePendingLidTracker()
    : _sequenceId(0),
      _lastCommitStarted(0),
      _lastCommitCompleted(0),
      _pending()
{}

TwoPhasePendingLidTracker::~TwoPhasePendingLidTracker() {
    assert(_pending.empty());
}

IPendingLidTracker::Token
TwoPhasePendingLidTracker::produce(uint32_t lid) {
    std::lock_guard guard(_mutex);
    _pending[lid] = ++_sequenceId;
    return Token(lid, *this);
}
void
TwoPhasePendingLidTracker::consume(uint32_t lid) {
    (void) lid;
}

ILidCommitState::State
TwoPhasePendingLidTracker::waitFor(MonitorGuard & guard, State state, uint32_t lid) const {
    for (auto found = _pending.find(lid); found != _pending.end(); found = _pending.find(lid)) {
        if (state == State::NEED_COMMIT) {
            if (found->second > _lastCommitStarted) {
                return State::NEED_COMMIT;
            }
            return State::WAITING;
        }
        _cond.wait(guard);
    }
    return State::COMPLETED;
}

void
TwoPhasePendingLidTracker::consumeSnapshot(uint64_t sequenceIdWhenStarted) {
    MonitorGuard guard(_mutex);
    assert(sequenceIdWhenStarted >= _lastCommitCompleted);
    _lastCommitCompleted = sequenceIdWhenStarted;
    std::vector<uint32_t> committed;
    for (const auto & entry : _pending) {
        if (entry.second <= sequenceIdWhenStarted)
            committed.push_back(entry.first);
    }
    for (uint32_t lid : committed) {
        _pending.erase(lid);
    }
    _cond.notify_all();
}

ILidCommitState::LidList
TwoPhasePendingLidTracker::pendingLids() const {
    MonitorGuard guard(_mutex);
    LidList lids;
    lids.reserve(_pending.size());
    for (const auto & entry : _pending) {
        lids.push_back(entry.first);
    }
    return lids;
}

namespace common::internal {

class CommitList : public PendingLidTrackerBase::Payload {
public:
    using LidList = ILidCommitState::LidList;
    CommitList(uint64_t commitStarted, TwoPhasePendingLidTracker & tracker)
        : _tracker(&tracker),
          _commitStarted(commitStarted)
    { }
    CommitList(const CommitList &) = delete;
    CommitList & operator = (const CommitList &) = delete;
    CommitList & operator = (CommitList &&) = delete;
    CommitList(CommitList && rhs) noexcept
        : _tracker(rhs._tracker),
          _commitStarted(rhs._commitStarted)
    {
        rhs._tracker = nullptr;
    }
    ~CommitList() override {
        if (_tracker != nullptr) {
            _tracker->consumeSnapshot(_commitStarted);
        }
    }
private:
    TwoPhasePendingLidTracker * _tracker;
    uint64_t                    _commitStarted;
};

}

PendingLidTrackerBase::Snapshot
TwoPhasePendingLidTracker::produceSnapshot() {
    MonitorGuard guard(_mutex);
    _lastCommitStarted = _sequenceId;
    return std::make_unique<common::internal::CommitList>(_lastCommitStarted, *this);
}

}
