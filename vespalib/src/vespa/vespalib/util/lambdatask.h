// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor.h"

namespace vespalib {

template <class FunctionType>
class LambdaTask : public vespalib::Executor::Task {
    FunctionType _func;

public:
    LambdaTask(const FunctionType &func) : _func(func) {}
    LambdaTask(FunctionType &&func) : _func(std::move(func)) {}
    LambdaTask(const LambdaTask &) = delete;
    LambdaTask & operator = (const LambdaTask &) = delete;
    ~LambdaTask() {}
    void run() override { _func(); }
};

template <class FunctionType>
vespalib::Executor::Task::UP
makeLambdaTask(FunctionType &&function)
{
    return std::make_unique<LambdaTask<std::decay_t<FunctionType>>>
        (std::forward<FunctionType>(function));
}

}
