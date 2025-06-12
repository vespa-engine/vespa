// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

void post(double endTime, Handler<Request> &handler,
          Request::Status status = Request::STATUS_OK)
{
    Request::UP req(new Request());
    req->status(status).endTime(endTime);
    handler.handle(std::move(req));
}

TEST(QpsAnalyzerTest, simulate_100_qps) {
    RequestSink f1;
    QpsAnalyzer f2(f1);
    for (size_t i = 1; i < 10000; ++i) {
        post(i * 0.01, f2);
        post(i * 0.01, f2, Request::STATUS_DROPPED);
        post(i * 0.01, f2, Request::STATUS_FAILED);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
