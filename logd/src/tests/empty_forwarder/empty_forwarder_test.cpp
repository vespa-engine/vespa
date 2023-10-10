// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <logd/empty_forwarder.h>
#include <logd/metrics.h>
#include <vespa/log/log.h>
#include <vespa/vespalib/metrics/dummy_metrics_manager.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace logdemon;
using vespalib::metrics::DummyMetricsManager;

struct MockMetricsManager : public DummyMetricsManager {
    int add_count;
    MockMetricsManager() noexcept : DummyMetricsManager(), add_count(0) {}
    void add(Counter::Increment) override {
        ++add_count;
    }
};

std::string
make_log_line(const std::string& level, const std::string& payload)
{
    return "1234.5678\tmy_host\t10/20\tmy_service\tmy_component\t" + level + "\t" + payload;
}

struct EmptyForwarderTest : public ::testing::Test {
    std::shared_ptr<MockMetricsManager> metrics_mgr;
    Metrics metrics;
    EmptyForwarder forwarder;

    EmptyForwarderTest()
            : metrics_mgr(std::make_shared<MockMetricsManager>()),
              metrics(metrics_mgr),
              forwarder(metrics) {
    }

    void forward_line(const std::string &payload) {
        forwarder.forwardLine(make_log_line("info", payload));
    }
    void forward_bad_line() {
        forwarder.forwardLine("badline");
    }
};

TEST_F(EmptyForwarderTest, bad_log_lines_are_counted)
{
    forward_bad_line();
    EXPECT_EQ(1, forwarder.badLines());
}

TEST_F(EmptyForwarderTest, metrics_are_updated_for_each_log_message)
{
    forward_line("a");
    EXPECT_EQ(1, metrics_mgr->add_count);
    forward_line("b");
    EXPECT_EQ(2, metrics_mgr->add_count);
}

GTEST_MAIN_RUN_ALL_TESTS()
