// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/sync.h>
#include <vespa/fastos/thread.h>

namespace search {

class Runnable : public FastOS_Runnable
{
protected:
    uint32_t _id;
    vespalib::Monitor _cond;
    bool _done;
    bool _stopped;

public:
    Runnable(uint32_t id) :
        _id(id), _cond(), _done(false), _stopped(false)
    { }
    void Run(FastOS_ThreadInterface *, void *) override {
        doRun();

        vespalib::MonitorGuard guard(_cond);
        _stopped = true;
        guard.broadcast();
    }
    virtual void doRun() = 0;
    void stop() {
        vespalib::MonitorGuard guard(_cond);
        _done = true;
    }
    void join() {
        vespalib::MonitorGuard guard(_cond);
        while (!_stopped) {
            guard.wait();
        }
    }
};

} // search

