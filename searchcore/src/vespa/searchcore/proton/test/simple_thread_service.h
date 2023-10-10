// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/i_thread_service.h>

namespace proton::test {

/**
 * Implementation of IThreadService that overrides isCurrentThread() to true.
 * Can be used by unit tests that do not care about that functions are executed in the correct
 * thread.
 */
class SimpleThreadService : public searchcorespi::index::IThreadService
{
private:
    searchcorespi::index::IThreadService &_service;

public:
    SimpleThreadService(searchcorespi::index::IThreadService &service)
        : _service(service)
    {
    }
    virtual vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) {
        return _service.execute(std::move(task));
    }
    virtual void run(vespalib::Runnable &runnable) {
        _service.run(runnable);
        // sync() because underlying implementation can use isCurrentThread()
        // to determine to run it directly.
        if (!_service.isCurrentThread()) {
            sync();
        }
    }
    virtual vespalib::Syncable &sync() {
        _service.sync();
        return *this;
    }
    virtual bool isCurrentThread() const {
        return true;
    }
};

}

