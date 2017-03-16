// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.ddbstate");

#include "ddbstate.h"
using proton::configvalidator::ResultType;


namespace proton {

std::vector<vespalib::string> DDBState::_stateNames =
{
    "CONSTRUCT",
    "LOAD",
    "REPLAY_TRANSACTION_LOG",
    "REDO_REPROCESS",
    "APPLY_LIVE_CONFIG",
    "REPROCESS",
    "ONLINE",
    "SHUTDOWN",
    "DEAD",
};

std::vector<vespalib::string> DDBState::_configStateNames =
{
    "OK",
    "NEED_RESTART",
    "REJECT"
};

DDBState::DDBState()
    : _state(State::CONSTRUCT),
      _configState(ConfigState::OK),
      _lock(),
      _cond()
{
}


DDBState::~DDBState()
{

}


bool
DDBState::enterLoadState()
{
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    assert(_state == State::CONSTRUCT);
    _state = State::LOAD;
    return true;
}

    
bool
DDBState::enterReplayTransactionLogState()
{
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    assert(_state == State::LOAD);
    _state = State::REPLAY_TRANSACTION_LOG;
    return true;
}


bool
DDBState::enterRedoReprocessState()
{
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    assert(_state == State::REPLAY_TRANSACTION_LOG);
    _state = State::REDO_REPROCESS;
    return true;
}


bool
DDBState::enterApplyLiveConfigState()
{
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    assert(_state == State::REPLAY_TRANSACTION_LOG ||
           _state == State::REDO_REPROCESS);
    _state = State::APPLY_LIVE_CONFIG;
    return true;
}


bool
DDBState::enterReprocessState()
{
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    assert(_state == State::APPLY_LIVE_CONFIG);
    _state = State::REPROCESS;
    return true;
}

bool
DDBState::enterOnlineState()
{
    Guard guard(_lock);
    if (getClosed()) {
        return false;
    }
    assert(_state == State::REPROCESS);
    _state = State::ONLINE;
    _cond.notify_all();
    return true;
}


void
DDBState::enterShutdownState()
{
    Guard guard(_lock);
    // Shutdown can be initiated before online state was reached
    if (getClosed()) {
        return;
    }
    _state = State::SHUTDOWN;
    _cond.notify_all();
}

void
DDBState::enterDeadState()
{
    Guard guard(_lock);
    if (_state == State::DEAD) {
        return;
    }
    assert(_state == State::SHUTDOWN);
    _state = State::DEAD;
    _cond.notify_all();
}


void
DDBState::setConfigState(ConfigState newConfigState)
{
    Guard guard(_lock);
    _configState = newConfigState;
}


void
DDBState::clearRejectedConfig()
{
    setConfigState(ConfigState::OK);
}


DDBState::ConfigState
DDBState::calcConfigState(const ResultType &cvr)
{
    if (_state < State::APPLY_LIVE_CONFIG) {
        // Config has been accepted, placed in transaction log and
        // activated by earlier instance. Rejecting config would cause
        // a divergent state.
        return ConfigState::OK;
    }
    switch (cvr) {
    case ResultType::OK:
        return ConfigState::OK;
    case ResultType::ATTRIBUTE_ASPECT_ADDED:
    case ResultType::ATTRIBUTE_FAST_ACCESS_ADDED:
    case ResultType::ATTRIBUTE_ASPECT_REMOVED:
    case ResultType::ATTRIBUTE_FAST_ACCESS_REMOVED:
        if (_state == State::APPLY_LIVE_CONFIG) {
            return ConfigState::OK;
        }
        return ConfigState::NEED_RESTART;
    default:
        return ConfigState::REJECT;
    }
}


vespalib::string
DDBState::getStateString(State state)
{
    return _stateNames[static_cast<unsigned int>(state)];
}


vespalib::string
DDBState::getConfigStateString(ConfigState configState)
{
    return _configStateNames[static_cast<unsigned int>(configState)];
}


void
DDBState::waitForOnlineState()
{
    GuardLock lk(_lock);
    _cond.wait(lk, [this] { return this->_state >= State::ONLINE; } );
}


} // namespace proton
