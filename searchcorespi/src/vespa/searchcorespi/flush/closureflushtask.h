// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flushtask.h"
#include <vespa/vespalib/util/closure.h>

namespace searchcorespi {

class ClosureFlushTask : public FlushTask
{
    std::unique_ptr<vespalib::Closure> _closure;
    search::SerialNum _flushSerial;

public:
    ClosureFlushTask(std::unique_ptr<vespalib::Closure> closure,
                     search::SerialNum flushSerial)
        : _closure(std::move(closure)),
          _flushSerial(flushSerial)
    {
    }
    
    search::SerialNum getFlushSerial() const override {
        return _flushSerial;
    }

    void run() override {
        _closure->call();
    }
};

/**
 * Wraps a Closure as a FlushTask
 **/
static inline FlushTask::UP
makeFlushTask(std::unique_ptr<vespalib::Closure> closure,
              search::SerialNum flushSerial)
{
    return FlushTask::UP(new ClosureFlushTask(std::move(closure), flushSerial));
}

} // namespace searchcorespi
