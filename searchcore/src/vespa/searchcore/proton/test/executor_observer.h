// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>

namespace proton::test {

class ExecutorObserver : public vespalib::Executor
{
private:
    vespalib::Executor &_executor;
    uint32_t _executeCnt;

public:
    ExecutorObserver(vespalib::Executor &executor)
        : _executor(executor),
          _executeCnt(0)
    {}

    uint32_t getExecuteCnt() const { return _executeCnt; }

    // Implements vespalib::Executor
    virtual Task::UP execute(Task::UP task) override {
        ++_executeCnt;
        return _executor.execute(std::move(task));
    }
};

}


