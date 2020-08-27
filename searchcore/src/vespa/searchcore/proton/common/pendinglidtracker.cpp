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

TwoPhasePendingLidTracker::TwoPhasePendingLidTracker() = default;

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
TwoPhasePendingLidTracker::waitFor(MonitorGuard & guard, State state, uint32_t lid) const {
    for (auto found = _pending.find(lid); found != _pending.end(); found = _pending.find(lid)) {
        if (state == State::NEED_COMMIT) {
            if ((found->second.inflight_feed > 0) || found->second.need_commit) {
                return State::NEED_COMMIT;
            }
            return State::WAITING;
        }
        _cond.wait(guard);
    }
    return State::COMPLETED;
}

void
TwoPhasePendingLidTracker::consumeSnapshot(LidList committed) {
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
    CommitList(LidList lids, TwoPhasePendingLidTracker & tracker)
        : _tracker(&tracker),
          _lids(std::move(lids))
    { }
    CommitList(const CommitList &) = delete;
    CommitList & operator = (const CommitList &) = delete;
    CommitList & operator = (CommitList &&) = delete;
    CommitList(CommitList && rhs) noexcept
        : _tracker(rhs._tracker),
          _lids(std::move(rhs._lids))
    {
        rhs._tracker = nullptr;
    }
    ~CommitList() override {
        if (_tracker != nullptr) {
            _tracker->consumeSnapshot(std::move(_lids));
        }
    }
private:
    TwoPhasePendingLidTracker * _tracker;
    LidList                     _lids;
};

}

PendingLidTrackerBase::Snapshot
TwoPhasePendingLidTracker::produceSnapshot() {
    LidList toCommit;
    MonitorGuard guard(_mutex);
    for (auto & entry : _pending) {
        if (entry.second.need_commit) {
            toCommit.emplace_back(entry.first);
            entry.second.inflight_commit ++;
            entry.second.need_commit = false;
        }
    }
    return std::make_unique<common::internal::CommitList>(std::move(toCommit), *this);
}

}
