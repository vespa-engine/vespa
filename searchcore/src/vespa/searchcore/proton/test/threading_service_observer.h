// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_observer.h"
#include "thread_service_observer.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchlib/common/sequencedtaskexecutorobserver.h>

namespace proton {
namespace test {

class ThreadingServiceObserver : public searchcorespi::index::IThreadingService
{
private:
    searchcorespi::index::IThreadingService &_service;
    ThreadServiceObserver _master;
    ThreadServiceObserver _index;
    search::SequencedTaskExecutorObserver _indexFieldInverter;
    search::SequencedTaskExecutorObserver _indexFieldWriter;
    search::SequencedTaskExecutorObserver _attributeFieldWriter;

public:
    ThreadingServiceObserver(searchcorespi::index::IThreadingService &service)
        : _service(service),
          _master(_service.master()),
          _index(service.index()),
          _indexFieldInverter(_service.indexFieldInverter()),
          _indexFieldWriter(_service.indexFieldWriter()),
          _attributeFieldWriter(_service.attributeFieldWriter())
    {
    }
    virtual ~ThreadingServiceObserver() override { }
    const ThreadServiceObserver &masterObserver() const {
        return _master;
    }
    const ThreadServiceObserver &indexObserver() const {
        return _index;
    }
    const search::SequencedTaskExecutorObserver &indexFieldInverterObserver()
        const
    {
        return _indexFieldInverter;
    }
    const search::SequencedTaskExecutorObserver &indexFieldWriterObserver()
        const
    {
        return _indexFieldWriter;
    }

    const search::SequencedTaskExecutorObserver &attributeFieldWriterObserver()
        const
    {
        return _attributeFieldWriter;
    }

    /**
     * Implements vespalib::Syncable
     */
    virtual vespalib::Syncable &sync() override {
        return _service.sync();
    }

    /**
     * Implements IThreadingService
     */
    virtual searchcorespi::index::IThreadService &master() override {
        return _master;
    }
    virtual searchcorespi::index::IThreadService &index() override {
        return _index;
    }
    virtual search::ISequencedTaskExecutor &indexFieldInverter() override {
        return _indexFieldInverter;
    }
    virtual search::ISequencedTaskExecutor &indexFieldWriter() override {
        return _indexFieldWriter;
    }

    virtual search::ISequencedTaskExecutor &attributeFieldWriter() override {
        return _attributeFieldWriter;
    }
};

} // namespace test
} // namespace proton


