// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "systemstate.h"
#include <vespa/vespalib/util/sync.h>

namespace documentapi {

/**
 * Implements a handover class to enable the system state handler to be perform handover even on const objects
 * such as occur when returning a handle by value from a function.
 */
class SystemStateHandover {
    friend class SystemStateHandle;
private:
    SystemStateHandover(const SystemStateHandover &);
    SystemStateHandover &operator=(const SystemStateHandover &);
    SystemStateHandover(SystemState *state, vespalib::LockGuard &guard);

private:
    SystemState                *_state;
    mutable vespalib::LockGuard _guard;
};

/**
 * Implements a handle to grant synchronized access to the content of a system state object. This needs the
 * above handover class to be able to return itself from methods that create it.
 */
class SystemStateHandle {
private:
    SystemState        *_state; // The associated system state for which this object is a handler.
    vespalib::LockGuard _guard; // The lock guard for the system state's lock.

    SystemState &operator=(const SystemStateHandle &rhs); // hide

public:
    /**
     * Creates a new system state handler object that grants access to the content of the supplied system
     * state object.  This handle is required to make sure that all access to the system state content is
     * locked.
     */
    SystemStateHandle(SystemState &state);

    /**
     * Implements the copy constructor.
     *
     * @param rhs The handle to copy to this.
     */
    SystemStateHandle(SystemStateHandle &rhs);

    /**
     * Implements the copy constructor for a const handle.
     *
     * @param rhs The handle to copy to this.
     */
    SystemStateHandle(const SystemStateHandover &rhs);

    /**
     * Destructor. Releases the contained lock on the associated system state object. There is no unlock()
     * mechanism provided, since this will happen automatically as soon as this handle goes out of scope.
     */
    ~SystemStateHandle();

    /** Implements a cast-operator for handover. */
    operator SystemStateHandover();

    /** Returns whether or not this handle is valid. */
    bool isValid() const { return _state != NULL; }

    /** Returns a reference to the root node of the associated system state. */
    NodeState &getRoot() { return *_state->_root; }

    /** Returns a const reference to the root node of the associated system state. */
    const NodeState &getRoot() const { return *_state->_root; }
};

}
