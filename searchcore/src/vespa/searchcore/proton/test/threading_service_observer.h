// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_observer.h"
#include "thread_service_observer.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchlib/common/sequencedtaskexecutorobserver.h>

namespace proton:: test {

class ThreadingServiceObserver : public searchcorespi::index::IThreadingService
{
private:
    searchcorespi::index::IThreadingService &_service;
    ThreadServiceObserver _master;
    ThreadServiceObserver _index;
    ThreadServiceObserver _summary;
    vespalib::ThreadExecutor & _shared;
    search::SequencedTaskExecutorObserver _indexFieldInverter;
    search::SequencedTaskExecutorObserver _indexFieldWriter;
    search::SequencedTaskExecutorObserver _attributeFieldWriter;

public:
    ThreadingServiceObserver(searchcorespi::index::IThreadingService &service)
        : _service(service),
          _master(_service.master()),
          _index(service.index()),
          _summary(service.summary()),
          _shared(service.shared()),
          _indexFieldInverter(_service.indexFieldInverter()),
          _indexFieldWriter(_service.indexFieldWriter()),
          _attributeFieldWriter(_service.attributeFieldWriter())
    {
    }
    ~ThreadingServiceObserver() override { }
    const ThreadServiceObserver &masterObserver() const {
        return _master;
    }
    const ThreadServiceObserver &indexObserver() const {
        return _index;
    }
    const ThreadServiceObserver &summaryObserver() const {
        return _summary;
    }
    const search::SequencedTaskExecutorObserver &indexFieldInverterObserver() const {
        return _indexFieldInverter;
    }
    const search::SequencedTaskExecutorObserver &indexFieldWriterObserver() const {
        return _indexFieldWriter;
    }

    const search::SequencedTaskExecutorObserver &attributeFieldWriterObserver() const {
        return _attributeFieldWriter;
    }

    /**
     * Implements vespalib::Syncable
     */
    vespalib::Syncable &sync() override {
        return _service.sync();
    }

    /**
     * Implements IThreadingService
     */
    searchcorespi::index::IThreadService &master() override {
        return _master;
    }
    searchcorespi::index::IThreadService &index() override {
        return _index;
    }
    searchcorespi::index::IThreadService &summary() override {
        return _summary;
    }
    vespalib::ThreadExecutor &shared() override {
        return _shared;
    }
    search::ISequencedTaskExecutor &indexFieldInverter() override {
        return _indexFieldInverter;
    }
    search::ISequencedTaskExecutor &indexFieldWriter() override {
        return _indexFieldWriter;
    }

    search::ISequencedTaskExecutor &attributeFieldWriter() override {
        return _attributeFieldWriter;
    }
};

}
