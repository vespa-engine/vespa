// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/ithreadingservice.h>

class FastOS_ThreadPool;

namespace vespalib { class TestClock; }

namespace proton {

class ExecutorThreadingService;

/**
 * Helper class contain a FNET_Transport object for use in tests.
 **/
class Transport {
public:
    Transport();
    virtual ~Transport();
    FNET_Transport & transport() { return *_transport; }
    FastOS_ThreadPool & threadPool() { return *_threadPool; }
    const vespalib::Clock & clock() const;
    virtual void shutdown();
private:
    std::unique_ptr<FastOS_ThreadPool> _threadPool;
    std::unique_ptr<FNET_Transport>    _transport;
    std::unique_ptr<vespalib::TestClock>   _clock;
};

class TransportAndExecutor : public Transport {
public:
    TransportAndExecutor(size_t num_threads);
    ~TransportAndExecutor() override;
    vespalib::Executor & shared() { return *_sharedExecutor; }
    vespalib::ISequencedTaskExecutor& field_writer() { return *_field_writer; }
    void shutdown() override;
private:
    std::unique_ptr<vespalib::Executor> _sharedExecutor;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _field_writer;

};

class TransportAndExecutorService : public TransportAndExecutor {
public:
    TransportAndExecutorService(size_t num_threads);
    ~TransportAndExecutorService() override;
    searchcorespi::index::IThreadingService & write();
    void shutdown() override;
private:
    std::unique_ptr<ExecutorThreadingService> _writeService;
};

}
