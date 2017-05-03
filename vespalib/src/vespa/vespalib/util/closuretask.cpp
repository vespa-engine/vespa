// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "closuretask.h"

namespace vespalib {

ClosureTask::~ClosureTask() {}

Executor::Task::UP makeTask(std::unique_ptr<Closure> closure) {
    return Executor::Task::UP(new ClosureTask(std::move(closure)));
}

}
