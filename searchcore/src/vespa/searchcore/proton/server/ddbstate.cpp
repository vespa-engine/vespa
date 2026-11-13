// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ddbstate.h"

#include <vespa/check_require.h>

#include <vespa/log/logf.h>

LOG_SETUP(".proton.server.ddbstate");

namespace proton {

std::vector<std::string> DDBState::_stateNames = {
    "CONSTRUCT",
    "LOAD",
    "REPLAY_TRANSACTION_LOG",
    "REDO_REPROCESS",
    "DOC_STORE_VALIDATION",
    "APPLY_LIVE_CONFIG",
    "REPROCESS",
    "ONLINE",
    "SHUTDOWN",
    "DEAD",
};

std::vector<std::string> DDBState::_configStateNames = {"OK", "NEED_RESTART"};

DDBState::DDBState(const DocTypeName& doc_type_name)
    : _state(State::CONSTRUCT),
      _configState(ConfigState::OK),
      _lock(),
      _cond(),
      _load_time(),
      _online_time(),
      _replay_time(),
      _doc_type_name(doc_type_name) {
}

DDBState::~DDBState() = default;

void DDBState::set_state(State state) {
    auto old_state = getState();
    _state.store(state, std::memory_order_release);
    LOGF(info, "Switched document type {} ddb state from {} to {}", _doc_type_name.getName(),
         getStateString(old_state), getStateString(state));
}

bool DDBState::enterLoadState() {
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    CHECK(getState() == State::CONSTRUCT);
    _load_time = std::chrono::system_clock::now();
    set_state(State::LOAD);
    return true;
}

bool DDBState::enterReplayTransactionLogState() {
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    CHECK(getState() == State::LOAD);
    _replay_time = std::chrono::system_clock::now();
    set_state(State::REPLAY_TRANSACTION_LOG);
    return true;
}

bool DDBState::enterRedoReprocessState() {
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    CHECK(getState() == State::REPLAY_TRANSACTION_LOG);
    set_state(State::REDO_REPROCESS);
    return true;
}

bool DDBState::enter_doc_store_validation_state() {
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    State state(getState());
    CHECK(state == State::REPLAY_TRANSACTION_LOG || state == State::REDO_REPROCESS);
    set_state(State::DOC_STORE_VALIDATION);
    return true;
}

bool DDBState::enterApplyLiveConfigState() {
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    State state(getState());
    CHECK(state == State::REPLAY_TRANSACTION_LOG || state == State::REDO_REPROCESS ||
          state == State::DOC_STORE_VALIDATION);
    set_state(State::APPLY_LIVE_CONFIG);
    return true;
}

bool DDBState::enterReprocessState() {
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    CHECK(getState() == State::APPLY_LIVE_CONFIG);
    set_state(State::REPROCESS);
    return true;
}

bool DDBState::enterOnlineState() {
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    CHECK(getState() == State::REPROCESS);
    _online_time = std::chrono::system_clock::now();
    set_state(State::ONLINE);
    _cond.notify_all();
    return true;
}

void DDBState::enterShutdownState() {
    Guard guard(_lock);
    // Shutdown can be initiated before online state was reached
    if (getClosed()) {
        return;
    }
    set_state(State::SHUTDOWN);
    _cond.notify_all();
}

void DDBState::enterDeadState() {
    Guard guard(_lock);
    if (getState() == State::DEAD) {
        return;
    }
    CHECK(getState() == State::SHUTDOWN);
    set_state(State::DEAD);
    _cond.notify_all();
}

void DDBState::setConfigState(ConfigState newConfigState) {
    Guard guard(_lock);
    _configState.store(newConfigState, std::memory_order_relaxed);
}

void DDBState::clearDelayedConfig() {
    setConfigState(ConfigState::OK);
}

std::string DDBState::getStateString(State state) {
    return _stateNames[static_cast<unsigned int>(state)];
}

std::string DDBState::getConfigStateString(ConfigState configState) {
    return _configStateNames[static_cast<unsigned int>(configState)];
}

void DDBState::waitForOnlineState() {
    GuardLock lk(_lock);
    _cond.wait(lk, [this] { return this->getState() >= State::ONLINE; });
}

} // namespace proton
