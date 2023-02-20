// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "request.h"
#include <vbench/core/provider.h>
#include <vbench/core/handler.h>
#include <vbench/http/http_connection_pool.h>
#include <vespa/vespalib/util/runnable.h>
#include <vespa/vespalib/util/thread.h>

namespace vbench {

/**
 * Obtains requests from a request provider, performs the requests and
 * passes the requests along to a request handler. Runs its own
 * internal thread that will stop when the request provider starts
 * handing out empty requests.
 **/
class Worker : public vespalib::Runnable
{
private:
    std::thread         _thread;
    Provider<Request>  &_provider;
    Handler<Request>   &_next;
    HttpConnectionPool &_pool;
    Timer              &_timer;

    void run() override;
public:
    using UP = std::unique_ptr<Worker>;
    Worker(Provider<Request> &provider, Handler<Request> &next,
           HttpConnectionPool &pool, Timer &timer);
    void join() { _thread.join(); }
};

} // namespace vbench
