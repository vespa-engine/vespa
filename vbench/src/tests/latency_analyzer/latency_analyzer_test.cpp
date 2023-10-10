// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

void post(double latency, Handler<Request> &handler,
          double startTime = 0.0, Request::Status status = Request::STATUS_OK)
{
    Request::UP req(new Request());
    req->status(status).startTime(startTime).endTime(startTime + latency);
    handler.handle(std::move(req));
}

TEST_FF("require that only OK requests are counted", RequestSink(), LatencyAnalyzer(f1)) {
    post(1.0, f2);
    post(2.0, f2, 3.0);
    post(10.0, f2, 0.0, Request::STATUS_DROPPED);
    post(20.0, f2, 0.0, Request::STATUS_FAILED);
    EXPECT_APPROX(1.0, f2.getStats().min, 10e-6);
    EXPECT_APPROX(1.5, f2.getStats().avg, 10e-6);
    EXPECT_APPROX(2.0, f2.getStats().max, 10e-6);
}

TEST_FF("verify percentiles", RequestSink(), LatencyAnalyzer(f1)) {
    for (size_t i = 0; i <= 10000; ++i) {
        post(0.001 * i, f2);
    }
    LatencyAnalyzer::Stats stats = f2.getStats();
    EXPECT_APPROX(5.0, stats.per50, 10e-6);
    EXPECT_APPROX(9.5, stats.per95, 10e-6);
    EXPECT_APPROX(9.9, stats.per99, 10e-6);
    fprintf(stderr, "%s", stats.toString().c_str());
}

TEST_MAIN() { TEST_RUN_ALL(); }
