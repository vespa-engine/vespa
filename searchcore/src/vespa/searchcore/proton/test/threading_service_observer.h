// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_observer.h"
#include "thread_service_observer.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/sequencedtaskexecutorobserver.h>

namespace proton:: test {

class ThreadingServiceObserver : public searchcorespi::index::IThreadingService
{
private:
    searchcorespi::index::IThreadingService &_service;
    ThreadServiceObserver _master;
    ThreadServiceObserver _index;
    ThreadServiceObserver _summary;
    vespalib::ThreadExecutor & _shared;
    vespalib::SequencedTaskExecutorObserver _indexFieldInverter;
    vespalib::SequencedTaskExecutorObserver _indexFieldWriter;
    vespalib::SequencedTaskExecutorObserver _attributeFieldWriter;

public:
    ThreadingServiceObserver(searchcorespi::index::IThreadingService &service);
    ~ThreadingServiceObserver() override;
    const ThreadServiceObserver &masterObserver() const {
        return _master;
    }
    const ThreadServiceObserver &indexObserver() const {
        return _index;
    }
    const ThreadServiceObserver &summaryObserver() const {
        return _summary;
    }
    const vespalib::SequencedTaskExecutorObserver &indexFieldInverterObserver() const {
        return _indexFieldInverter;
    }
    const vespalib::SequencedTaskExecutorObserver &indexFieldWriterObserver() const {
        return _indexFieldWriter;
    }

    const vespalib::SequencedTaskExecutorObserver &attributeFieldWriterObserver() const {
        return _attributeFieldWriter;
    }

    vespalib::Syncable &sync() override {
        return _service.sync();
    }

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
    vespalib::ISequencedTaskExecutor &indexFieldInverter() override {
        return _indexFieldInverter;
    }
    vespalib::ISequencedTaskExecutor &indexFieldWriter() override {
        return _indexFieldWriter;
    }

    vespalib::ISequencedTaskExecutor &attributeFieldWriter() override {
        return _attributeFieldWriter;
    }

};

}
