// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handler.h"
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/runnable.h>
#include <vespa/vespalib/util/joinable.h>

namespace vbench {

/**
 * A Handler that will forward incoming objects to another handler in
 * a separate thread. All objects are forwarded using the same thread,
 * reducing the need for synchronization in the handler forwarded
 * to. A call to join will wait until all queued object have been
 * forwarded. Object obtained after join is invoked will be discarded.
 **/
template <typename T>
class HandlerThread : public Handler<T>,
                      public vespalib::Runnable,
                      public vespalib::Joinable
{
private:
    std::mutex                                 _lock;
    std::condition_variable                    _cond;
    vespalib::ArrayQueue<std::unique_ptr<T> >  _queue;
    Handler<T>                                &_next;
    vespalib::Thread                           _thread;
    bool                                       _done;

    void run() override;

public:
    HandlerThread(Handler<T> &next, init_fun_t init_fun);
    ~HandlerThread();
    void handle(std::unique_ptr<T> obj) override;
    void join() override;
};

} // namespace vbench

#include "handler_thread.hpp"

