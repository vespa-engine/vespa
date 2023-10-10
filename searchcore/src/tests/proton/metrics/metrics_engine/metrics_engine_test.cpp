// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/metricset.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/metrics_engine.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("metrics_engine_test");

using namespace proton;

struct DummyMetricSet : public metrics::MetricSet {
    DummyMetricSet(const vespalib::string &name) : metrics::MetricSet(name, {}, "", nullptr) {}
};

struct AttributeMetricsFixture {
    MetricsEngine engine;
    DummyMetricSet parent;
    AttributeMetrics metrics;
    AttributeMetricsFixture()
        : engine(),
          parent("parent"),
          metrics(&parent)
    {}
    void addAttribute(const vespalib::string &attrName) {
        engine.addAttribute(metrics, attrName);
    }
    void removeAttribute(const vespalib::string &attrName) {
        engine.removeAttribute(metrics, attrName);
    }
    void cleanAttributes() {
        engine.cleanAttributes(metrics);
    }
    void assertRegisteredMetrics(size_t expNumMetrics) const {
        EXPECT_EQUAL(expNumMetrics, parent.getRegisteredMetrics().size());
    }
    void assertMetricsExists(const vespalib::string &attrName) {
        EXPECT_TRUE(metrics.get(attrName) != nullptr);
    }
    void assertMetricsNotExists(const vespalib::string &attrName) {
        EXPECT_TRUE(metrics.get(attrName) == nullptr);
    }
};

TEST_F("require that attribute metrics can be added", AttributeMetricsFixture)
{
    TEST_DO(f.assertRegisteredMetrics(0));
    f.addAttribute("foo");
    TEST_DO(f.assertRegisteredMetrics(1));
    TEST_DO(f.assertMetricsExists("foo"));
}

TEST_F("require that attribute metrics can be removed", AttributeMetricsFixture)
{
    TEST_DO(f.assertRegisteredMetrics(0));
    f.addAttribute("foo");
    f.addAttribute("bar");
    TEST_DO(f.assertRegisteredMetrics(2));
    f.removeAttribute("foo");
    TEST_DO(f.assertRegisteredMetrics(1));
    TEST_DO(f.assertMetricsNotExists("foo"));
    TEST_DO(f.assertMetricsExists("bar"));
}

TEST_F("require that all attribute metrics can be cleaned", AttributeMetricsFixture)
{
    TEST_DO(f.assertRegisteredMetrics(0));
    f.addAttribute("foo");
    f.addAttribute("bar");
    TEST_DO(f.assertRegisteredMetrics(2));
    f.cleanAttributes();
    TEST_DO(f.assertRegisteredMetrics(0));
    TEST_DO(f.assertMetricsNotExists("foo"));
    TEST_DO(f.assertMetricsNotExists("bar"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
