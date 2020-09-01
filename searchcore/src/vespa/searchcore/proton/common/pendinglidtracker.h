// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <condition_variable>
#include <vector>

namespace proton {

/** Interface for tracking lids in the feed pipeline.
 * A token is created with produce(lid).
 * Once the token goes out of scope the lid is then consumed.
 * This is used to track which lids are inflight in the feed pipeline.
 */
class IPendingLidTracker {
public:
    class Token {
    public:
        Token();
        Token(uint32_t lid, IPendingLidTracker & tracker);
        Token(const Token &) = delete;
        Token & operator = (const Token &) = delete;
        Token & operator = (Token &&) = delete;
        Token(Token && rhs) noexcept
            : _tracker(rhs._tracker),
              _lid(rhs._lid)
        {
            rhs._tracker = nullptr;
        }
        ~Token();
    private:
        IPendingLidTracker * _tracker;
        uint32_t            _lid;
    };
    virtual ~IPendingLidTracker() = default;
    virtual Token produce(uint32_t lid) = 0;
private:
    virtual void consume(uint32_t lid) = 0;
};

/**
 * This is an interface for checking/waiting the state of a lid in the feed pipeline
 * The lid might need a commit (NEED_COMMIT), but if visibility-delay is zero it will go directly to WAITING
 * as no explicit commit is needed.
 * After a commit has been started the lid is transferred to WAITING.
 * Once the commit has gone through the lid is in state COMPLETED.
 */
class ILidCommitState {
public:
    enum class State {NEED_COMMIT, WAITING, COMPLETED};
    using LidList = std::vector<uint32_t>;
    virtual ~ILidCommitState() = default;
    State getState() const { return waitState(State::NEED_COMMIT); }
    State getState(uint32_t lid) const { return waitState(State::NEED_COMMIT, lid); }
    State getState(const LidList & lids) const { return waitState(State::NEED_COMMIT, lids); }
    void waitComplete(uint32_t lid) const;
    void waitComplete(const LidList & lids) const;
    void waitComplete() const;
private:
    virtual State waitState(State state, uint32_t lid) const = 0;
    virtual State waitState(State state, const LidList & lids) const = 0;
    virtual State waitState(State state) const = 0;
};

/**
 * Base class for doing 2 phased lid tracking. The first phase is from when the feed operation
 * is in progress and lasts until the OperationDoneContext goes out of scope. This might include commit
 * when visibility-delay is zero.
 * When a commit is started a snapshot containing all lids in state NEED_COMMIT are taken,
 * while also moving the lids to WAITING. Once the snapshot goes out of scope when the commit is complete,
 * it will cleanup and move all lids from WAITING to COMPLETE.
 */
class PendingLidTrackerBase : public IPendingLidTracker,
                              public ILidCommitState
{
public:
    ~PendingLidTrackerBase();
    struct Payload {
        virtual ~Payload() = default;
    };
    using Snapshot = std::unique_ptr<Payload>;
    virtual Snapshot produceSnapshot() = 0;

    State waitState(State state) const override;
    State waitState(State state, uint32_t lid) const override;
    State waitState(State state, const LidList & lids) const override;
protected:
    using MonitorGuard = std::unique_lock<std::mutex>;
    PendingLidTrackerBase();
    virtual LidList pendingLids() const = 0;
    virtual State waitFor(MonitorGuard & guard, State state, uint32_t lid) const = 0;
    MonitorGuard getGuard() { return MonitorGuard(_mutex); }
    mutable std::mutex                     _mutex;
    mutable std::condition_variable        _cond;
};

/**
 * Use for tracking lids when visibility-delay is zero and commit is implicit.
 * In this case lids go directly to WAITING and the second phase is a noop.
 */
class PendingLidTracker : public PendingLidTrackerBase
{
public:
    PendingLidTracker();
    ~PendingLidTracker() override;
    Token produce(uint32_t lid) override;
    Snapshot produceSnapshot() override;
private:
    LidList pendingLids() const override;
    void consume(uint32_t lid) override;
    State waitFor(MonitorGuard & guard, State state, uint32_t lid) const override;

    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

namespace common::internal {
    class CommitList;
}
/**
 * Use for tracking lids in 2 phases which is needed when visibility-delay is non-zero.
 * It tracks lids that are in feed pipeline, lids where commit has been started and when they fully complete.
 */
class TwoPhasePendingLidTracker : public PendingLidTrackerBase
{
public:
    TwoPhasePendingLidTracker();
    ~TwoPhasePendingLidTracker() override;
    Token produce(uint32_t lid) override;
    Snapshot produceSnapshot() override;
private:
    friend common::internal::CommitList;
    void consume(uint32_t lid) override;
    void consumeSnapshot(uint64_t sequenceIdWhenStarted);
    LidList pendingLids() const override;
    State waitFor(MonitorGuard & guard, State state, uint32_t lid) const override;
    uint64_t _sequenceId;
    uint64_t _lastCommitStarted;
    uint64_t _lastCommitCompleted;
    vespalib::hash_map<uint32_t, uint64_t> _pending;
};

}
