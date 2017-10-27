// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "systemstate.h"
#include <vespa/vespalib/util/sync.h>

namespace documentapi {

/**
 * Implements a handle to grant synchronized access to the content of a system state object.
 */
class SystemStateHandle {
private:
    SystemState        *_state; // The associated system state for which this object is a handler.
    vespalib::LockGuard _guard; // The lock guard for the system state's lock.

    SystemStateHandle &operator=(const SystemStateHandle &) = delete;
    SystemStateHandle(const SystemStateHandle &) = delete;

public:
    /**
     * Creates a new system state handler object that grants access to the content of the supplied system
     * state object.  This handle is required to make sure that all access to the system state content is
     * locked.
     */
    SystemStateHandle(SystemState &state);

    /**
     * Implements the move constructor.
     *
     * @param rhs The handle to move to this.
     */
    SystemStateHandle(SystemStateHandle &&rhs);

    SystemStateHandle &operator=(SystemStateHandle &&rhs);
    /**
     * Destructor. Releases the contained lock on the associated system state object. There is no unlock()
     * mechanism provided, since this will happen automatically as soon as this handle goes out of scope.
     */
    ~SystemStateHandle();

    /** Returns whether or not this handle is valid. */
    bool isValid() const { return _state != NULL; }

    /** Returns a reference to the root node of the associated system state. */
    NodeState &getRoot() { return *_state->_root; }

    /** Returns a const reference to the root node of the associated system state. */
    const NodeState &getRoot() const { return *_state->_root; }
};

}
