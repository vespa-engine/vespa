// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flushtask.h"

namespace searchcorespi {

template <class FunctionType>
class LambdaFlushTask : public FlushTask {
    FunctionType _func;
    search::SerialNum _flushSerial;

public:
    LambdaFlushTask(FunctionType &&func, search::SerialNum flushSerial)
        : _func(std::move(func)),
          _flushSerial(flushSerial)
    {}
    ~LambdaFlushTask() override = default;
    search::SerialNum getFlushSerial() const override { return _flushSerial; }
    void run() override { _func(); }
};

template <class FunctionType>
std::unique_ptr<FlushTask>
makeLambdaFlushTask(FunctionType &&function, search::SerialNum flushSerial)
{
    return std::make_unique<LambdaFlushTask<std::decay_t<FunctionType>>>
            (std::forward<FunctionType>(function), flushSerial);
}

}
