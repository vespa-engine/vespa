// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
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
    State getState(uint32_t lid) const { return waitState(State::NEED_COMMIT, lid); }
    State getState(const LidList & lids) const { return waitState(State::NEED_COMMIT, lids); }
    void waitComplete(uint32_t lid) const;
    void waitComplete(const LidList & lids) const;
private:
    virtual State waitState(State state, uint32_t lid) const = 0;
    virtual State waitState(State state, const LidList & lids) const = 0;
};

}
