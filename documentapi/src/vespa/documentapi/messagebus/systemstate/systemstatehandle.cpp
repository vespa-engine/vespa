// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "systemstatehandle.h"

using namespace documentapi;

SystemStateHandover::SystemStateHandover(SystemState *state, vespalib::LockGuard &guard) :
    _state(state),
    _guard(guard)
{}

SystemStateHandle::SystemStateHandle(SystemState &state) :
    _state(&state),
    _guard(*state._lock)
{}

SystemStateHandle::SystemStateHandle(SystemStateHandle &rhs) :
    _state(rhs._state),
    _guard(rhs._guard)
{
    rhs._state = nullptr;
}

SystemStateHandle::SystemStateHandle(const SystemStateHandover &rhs) :
    _state(rhs._state),
    _guard(rhs._guard)
{}

SystemStateHandle::~SystemStateHandle() {}

SystemStateHandle::operator
SystemStateHandover() {
    SystemStateHandover ret(_state, _guard);
    _state = nullptr;
    return ret;
}

