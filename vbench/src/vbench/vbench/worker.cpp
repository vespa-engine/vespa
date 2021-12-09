// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "worker.h"
#include <vbench/http/http_client.h>

namespace vbench {

VESPA_THREAD_STACK_TAG(vbench_worker_thread);

void
Worker::run()
{
    for (;;) {
        Request::UP request = _provider.provide();
        if (request.get() == 0) {
            break;
        }
        request->startTime(_timer.sample());
        HttpClient::fetch(_pool, request->server(), request->url(), *request);
        request->endTime(_timer.sample());
        _next.handle(std::move(request));
    }
}

Worker::Worker(Provider<Request> &provider, Handler<Request> &next,
               HttpConnectionPool &pool, Timer &timer)
    : _thread(*this, vbench_worker_thread),
      _provider(provider),
      _next(next),
      _pool(pool),
      _timer(timer)
{
    _thread.start();
}

} // namespace vbench
