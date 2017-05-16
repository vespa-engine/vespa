// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>

namespace search {

template <class FunctionType>
class LambdaTask : public vespalib::Executor::Task {
    FunctionType _func;

public:
    LambdaTask(const FunctionType &func) : _func(func) {}
    LambdaTask(FunctionType &&func) : _func(std::move(func)) {}
    void run() override { _func(); }
};

template <class FunctionType>
inline vespalib::Executor::Task::UP
makeLambdaTask(FunctionType &&function)
{
    return std::make_unique<LambdaTask<std::decay_t<FunctionType>>>
        (std::forward<FunctionType>(function));
}

} // namespace search
