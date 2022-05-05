// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <vector>

namespace proton {

/**
 * Track document db main state and validate that state transitions follow
 * legal edges.
 *
 * Note that SHUTDOWN state can be entered from almost any state.
 */

class DDBState
{
public:
    enum class State {
        CONSTRUCT,
        LOAD,
        REPLAY_TRANSACTION_LOG,
        REDO_REPROCESS,
        APPLY_LIVE_CONFIG,
        REPROCESS,
        ONLINE,
        SHUTDOWN,
        DEAD
    };

    enum class ConfigState {
        OK,
        NEED_RESTART
    };
private:

    std::atomic<State>       _state;
    std::atomic<ConfigState> _configState;

    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;
    using GuardLock = std::unique_lock<Mutex>;
    Mutex     _lock;  // protects state transition
    std::condition_variable       _cond;

    static std::vector<vespalib::string> _stateNames;
    static std::vector<vespalib::string> _configStateNames;

    void set_state(State state) noexcept { _state.store(state, std::memory_order_relaxed); }

public:
    DDBState();
    ~DDBState();

    /**
     * Try to enter LOAD state.  Fail and return false if document db is
     * being shut down.
     */ 
    bool enterLoadState();
    bool enterReplayTransactionLogState();
    bool enterRedoReprocessState();
    bool enterApplyLiveConfigState();
    bool enterReprocessState();
    bool enterOnlineState();
    void enterShutdownState();
    void enterDeadState();
    State getState() const noexcept { return _state.load(std::memory_order_relaxed); }
    static vespalib::string getStateString(State state);
    
    bool getClosed() const noexcept {
        State state(getState());
        return state >= State::SHUTDOWN;
    }

    bool getAllowReconfig() const noexcept {
        State state(getState());
        return state >= State::APPLY_LIVE_CONFIG && state < State::SHUTDOWN;
    }

    bool getAllowPrune() const noexcept {
        State state(getState());
        return state == State::ONLINE;
    }
    
    static bool getDelayedConfig(ConfigState state) noexcept {
        return state != ConfigState::OK;
    }
    
    bool getDelayedConfig() const noexcept {
        ConfigState state(getConfigState());
        return getDelayedConfig(state);
    }

    bool get_load_done() const noexcept {
        State state(getState());
        return state >= State::REPLAY_TRANSACTION_LOG;
    }

    void clearDelayedConfig();
    ConfigState getConfigState() const noexcept { return _configState.load(std::memory_order_relaxed); }
    static vespalib::string getConfigStateString(ConfigState configState);
    void setConfigState(ConfigState newConfigState);
    void waitForOnlineState();
};

} // namespace proton
