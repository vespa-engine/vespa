// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "worker.h"
#include "dropped_tagger.h"
#include <vbench/core/time_queue.h>
#include <vbench/core/dispatcher.h>
#include <vbench/core/handler_thread.h>
#include <mutex>
#include <condition_variable>

namespace vbench {

/**
 * Component responsible for dispatching requests to workers at the
 * appropriate time based on what start time the requests are tagged
 * with.
 **/
class RequestScheduler : public Handler<Request>,
                         public vespalib::Runnable
{
private:
    Timer                   _timer;
    HandlerThread<Request>  _proxy;
    TimeQueue<Request>      _queue;
    DroppedTagger           _droppedTagger;
    Dispatcher<Request>     _dispatcher;
    std::thread             _thread;
    HttpConnectionPool      _connectionPool;
    std::vector<Worker::UP> _workers;
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _may_slumber;
    
    void run() override;
public:
    using UP = std::unique_ptr<RequestScheduler>;
    using CryptoEngine = vespalib::CryptoEngine;
    RequestScheduler(CryptoEngine::SP crypto, Handler<Request> &next, size_t numWorkers);
    void abort();
    void handle(Request::UP request) override;
    void start();
    RequestScheduler &stop();
    void join();
};

} // namespace vbench
