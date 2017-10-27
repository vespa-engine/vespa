// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "systemstatehandle.h"

using namespace documentapi;

SystemStateHandle::SystemStateHandle(SystemState &state) :
    _state(&state),
    _guard(*state._lock)
{}

SystemStateHandle::SystemStateHandle(SystemStateHandle &&rhs) :
    _state(rhs._state),
    _guard(std::move(rhs._guard))
{
    rhs._state = nullptr;
}

SystemStateHandle &
SystemStateHandle::operator=(SystemStateHandle &&rhs)
{
    if (this != &rhs) {
        _state = rhs._state;
        _guard = std::move(rhs._guard);
        rhs._state = nullptr;
    }
    return *this;
}

SystemStateHandle::~SystemStateHandle() {}

