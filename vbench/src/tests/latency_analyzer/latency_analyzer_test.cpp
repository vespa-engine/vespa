// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

void post(double latency, Handler<Request> &handler,
          double startTime = 0.0, Request::Status status = Request::STATUS_OK)
{
    Request::UP req(new Request());
    req->status(status).startTime(startTime).endTime(startTime + latency);
    handler.handle(std::move(req));
}

TEST(LatencyAnalyzerTest, require_that_only_OK_requests_are_counted) {
    RequestSink f1;
    LatencyAnalyzer f2(f1);
    post(1.0, f2);
    post(2.0, f2, 3.0);
    post(10.0, f2, 0.0, Request::STATUS_DROPPED);
    post(20.0, f2, 0.0, Request::STATUS_FAILED);
    EXPECT_NEAR(1.0, f2.getStats().min, 10e-6);
    EXPECT_NEAR(1.5, f2.getStats().avg, 10e-6);
    EXPECT_NEAR(2.0, f2.getStats().max, 10e-6);
}

TEST(LatencyAnalyzerTest, verify_percentiles) {
    RequestSink f1;
    LatencyAnalyzer f2(f1);
    for (size_t i = 0; i <= 10000; ++i) {
        post(0.001 * i, f2);
    }
    LatencyAnalyzer::Stats stats = f2.getStats();
    EXPECT_NEAR(5.0, stats.per50, 10e-6);
    EXPECT_NEAR(9.5, stats.per95, 10e-6);
    EXPECT_NEAR(9.9, stats.per99, 10e-6);
    fprintf(stderr, "%s", stats.toString().c_str());
}

GTEST_MAIN_RUN_ALL_TESTS()
