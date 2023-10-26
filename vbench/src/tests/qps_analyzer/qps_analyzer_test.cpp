// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

void post(double endTime, Handler<Request> &handler,
          Request::Status status = Request::STATUS_OK)
{
    Request::UP req(new Request());
    req->status(status).endTime(endTime);
    handler.handle(std::move(req));
}

TEST_FF("simulate 100 qps", RequestSink(), QpsAnalyzer(f1)) {
    for (size_t i = 1; i < 10000; ++i) {
        post(i * 0.01, f2);
        post(i * 0.01, f2, Request::STATUS_DROPPED);
        post(i * 0.01, f2, Request::STATUS_FAILED);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
