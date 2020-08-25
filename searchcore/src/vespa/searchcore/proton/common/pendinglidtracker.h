// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <condition_variable>
#include <vector>

namespace proton {

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

class ILidCommitState {
public:
    virtual ~ILidCommitState() = default;
    bool needCommit(uint32_t lid);
    bool needCommit(const std::vector<uint32_t> & lids);
    bool needCommit();
    void waitComplete(uint32_t lid);
    void waitComplete(const std::vector<uint32_t> & lids);
    void waitComplete();
protected:
    enum class State {NEED_COMMIT, COMPLETE};
    virtual State waitState(State state, uint32_t lid) = 0;
    virtual State waitState(State state, const std::vector<uint32_t> & lids) = 0;
    virtual State waitState(State state) = 0;
};

class PendingLidTrackerBase : public IPendingLidTracker,
                              public ILidCommitState
{
public:
    struct Payload {
        virtual ~Payload() = default;
    };
    using Snapshot = std::unique_ptr<Payload>;
    virtual Snapshot produceSnapshot() = 0;
private:
};

class PendingLidTracker : public PendingLidTrackerBase
{
public:
    PendingLidTracker();
    ~PendingLidTracker() override;
    Token produce(uint32_t lid) override;
    Snapshot produceSnapshot() override;
private:
    State waitState(State state) override;
    State waitState(State state, uint32_t lid) override;
    State waitState(State state, const std::vector<uint32_t> & lids) override;
    void consume(uint32_t lid) override;
    using MonitorGuard = std::unique_lock<std::mutex>;
    State waitFor(MonitorGuard & guard, State state, uint32_t lid);
    std::mutex                             _mutex;
    std::condition_variable                _cond;
    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

class TwoPhasePendingLidTracker : public PendingLidTrackerBase
{
public:
    TwoPhasePendingLidTracker();
    ~TwoPhasePendingLidTracker() override;
    Token produce(uint32_t lid) override;
    Snapshot produceSnapshot() override;
private:
    using List = std::vector<uint32_t>;
    class CommitList : public Payload {
    public:
        CommitList(List lids, TwoPhasePendingLidTracker & tracker);
        CommitList(const CommitList &) = delete;
        CommitList & operator = (const CommitList &) = delete;
        CommitList & operator = (CommitList &&) = delete;
        CommitList(CommitList && rhs) noexcept;
        ~CommitList() override;
    private:
        TwoPhasePendingLidTracker * _tracker;
        List                        _lids;
    };
    using MonitorGuard = std::unique_lock<std::mutex>;
    State waitState(State state) override;
    State waitState(State state, uint32_t lid) override;
    State waitState(State state, const std::vector<uint32_t> & lids) override;
    void consume(uint32_t lid) override;
    void consumeSnapshot(List);
    State waitFor(MonitorGuard & guard, State state, uint32_t lid);
    std::mutex                             _mutex;
    std::condition_variable                _cond;
    struct Counters {
        Counters() : inflight_feed(0), inflight_commit(0), need_commit(false) {}
        bool empty() const { return (inflight_feed == 0) && ! need_commit && (inflight_commit == 0); }
        uint32_t inflight_feed;
        uint32_t inflight_commit;
        bool need_commit;
    };
    vespalib::hash_map<uint32_t, Counters> _pending;
};

}
