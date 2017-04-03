// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "closure.h"
#include "executor.h"

namespace vespalib {

/**
 * Wrapper class for using closures as tasks to an executor.
 **/
class ClosureTask : public Executor::Task {
    std::unique_ptr<Closure> _closure;

public:
    ClosureTask(std::unique_ptr<Closure> closure) : _closure(std::move(closure)) {}
    void run() override { _closure->call(); }
};

/**
 * Wraps a Closure as an Executor::Task.
 **/
static inline Executor::Task::UP makeTask(std::unique_ptr<Closure> closure) {
    return Executor::Task::UP(new ClosureTask(std::move(closure)));
}

}  // namespace vespalib

