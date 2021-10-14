// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "request.h"
#include <vbench/core/provider.h>
#include <vbench/core/handler.h>
#include <vbench/http/http_connection_pool.h>
#include <vespa/vespalib/util/runnable.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/joinable.h>

namespace vbench {

/**
 * Obtains requests from a request provider, performs the requests and
 * passes the requests along to a request handler. Runs its own
 * internal thread that will stop when the request provider starts
 * handing out empty requests.
 **/
class Worker : public vespalib::Runnable,
               public vespalib::Joinable
{
private:
    vespalib::Thread    _thread;
    Provider<Request>  &_provider;
    Handler<Request>   &_next;
    HttpConnectionPool &_pool;
    Timer              &_timer;

    void run() override;
public:
    typedef std::unique_ptr<Worker> UP;
    Worker(Provider<Request> &provider, Handler<Request> &next,
           HttpConnectionPool &pool, Timer &timer);
    void join() override { _thread.join(); }
};

} // namespace vbench
