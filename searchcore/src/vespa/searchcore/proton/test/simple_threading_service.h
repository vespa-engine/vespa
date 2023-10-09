// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_thread_service.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>

namespace proton::test {

/**
 * Implementation of IThreadingService that overrides IThreadService::isCurrentThread() to true.
 * Can be used by unit tests that do not care about that functions are executed in the correct
 * thread.
 */
class SimpleThreadingService : public searchcorespi::index::IThreadingService
{
private:
    searchcorespi::index::IThreadingService &_service;
    SimpleThreadService _master;
    SimpleThreadService _index;

public:
    SimpleThreadingService(searchcorespi::index::IThreadingService &service)
        : _service(service),
          _master(_service.master()),
          _index(_service.index())
    {
    }
    virtual vespalib::Syncable &sync() {
        return _service.sync();
    }
    virtual searchcorespi::index::IThreadService &master() {
        return _master;
    }
    virtual searchcorespi::index::IThreadService &index() {
        return _index;
    }
    virtual search::ISequencedTaskExecutor &indexFieldInverter() {
        return _service.indexFieldInverter();
    }
    virtual search::ISequencedTaskExecutor &indexFieldWriter() {
        return _service.indexFieldWriter();
    }

    virtual search::ISequencedTaskExecutor &attributeFieldWriter() {
        return _service.attributeFieldWriter();
    }
};

}


