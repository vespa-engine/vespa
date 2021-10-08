// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
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

    State          _state;
    ConfigState    _configState;

    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;
    using GuardLock = std::unique_lock<Mutex>;
    Mutex     _lock;  // protects state transition
    std::condition_variable       _cond;

    static std::vector<vespalib::string> _stateNames;
    static std::vector<vespalib::string> _configStateNames;

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
    State getState() const { return _state; }
    static vespalib::string getStateString(State state);
    
    bool getClosed() const{
        State state(_state);
        return state >= State::SHUTDOWN;
    }

    bool getAllowReconfig() const {
        State state(_state);
        return state >= State::APPLY_LIVE_CONFIG && state < State::SHUTDOWN;
    }

    bool getAllowPrune() const {
        State state(_state);
        return state == State::ONLINE;
    }
    
    static bool getDelayedConfig(ConfigState state) {
        return state != ConfigState::OK;
    }
    
    bool getDelayedConfig() const {
        ConfigState state(_configState);
        return getDelayedConfig(state);
    }

    void clearDelayedConfig();
    ConfigState getConfigState() const { return _configState; }
    static vespalib::string getConfigStateString(ConfigState configState);
    void setConfigState(ConfigState newConfigState);
    void waitForOnlineState();
};

} // namespace proton
