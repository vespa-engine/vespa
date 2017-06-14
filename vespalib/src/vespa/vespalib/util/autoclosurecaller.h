// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "closure.h"

namespace vespalib {

/**
 * @brief RAII class that calls a closure in its destructor.
 *
 * To make sure a closure is called when a scope is exited, an
 * instance of this class can be kept on the stack.
 */
class AutoClosureCaller {
    std::unique_ptr<Closure> _closure;

public:
    /**
     * Creates a guard object that will call a closure on destruction.
     * @param closure closure to call
     */
    AutoClosureCaller(std::unique_ptr<Closure> closure) : _closure(std::move(closure)) {}

    /**
     * Calls the registered closure.
     */
    ~AutoClosureCaller() { _closure->call(); }
};

}  // namespace proton

