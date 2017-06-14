// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for metrics_engine.

#include <vespa/log/log.h>
LOG_SETUP("metrics_engine_test");

#include <vespa/metrics/metricset.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics_collection.h>
#include <vespa/searchcore/proton/metrics/metrics_engine.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace proton;

struct DummyMetricSet : public metrics::MetricSet {
    DummyMetricSet(const vespalib::string &name) : metrics::MetricSet(name, "", "", nullptr) {}
};

struct AttributeMetricsFixture {
    MetricsEngine engine;
    DummyMetricSet parent;
    AttributeMetrics metrics;
    LegacyAttributeMetrics legacyMetrics;
    LegacyAttributeMetrics totalLegacyMetrics;
    AttributeMetricsFixture()
        : engine(),
          parent("parent"),
          metrics(&parent),
          legacyMetrics(nullptr),
          totalLegacyMetrics(nullptr)
    {}
    void addAttribute(const vespalib::string &attrName) {
        engine.addAttribute(AttributeMetricsCollection(metrics, legacyMetrics), &totalLegacyMetrics, attrName);
    }
    void removeAttribute(const vespalib::string &attrName) {
        engine.removeAttribute(AttributeMetricsCollection(metrics, legacyMetrics), &totalLegacyMetrics, attrName);
    }
    void cleanAttributes() {
        engine.cleanAttributes(AttributeMetricsCollection(metrics, legacyMetrics), &totalLegacyMetrics);
    }
    void assertRegisteredMetrics(size_t expNumMetrics) const {
        EXPECT_EQUAL(expNumMetrics, parent.getRegisteredMetrics().size());
        EXPECT_EQUAL(expNumMetrics, legacyMetrics.list.getRegisteredMetrics().size());
        EXPECT_EQUAL(expNumMetrics, totalLegacyMetrics.list.getRegisteredMetrics().size());
    }
    void assertMetricsExists(const vespalib::string &attrName) {
        EXPECT_TRUE(metrics.get(attrName) != nullptr);
        EXPECT_TRUE(legacyMetrics.list.get(attrName) != nullptr);
        EXPECT_TRUE(totalLegacyMetrics.list.get(attrName) != nullptr);
    }
    void assertMetricsNotExists(const vespalib::string &attrName) {
        EXPECT_TRUE(metrics.get(attrName) == nullptr);
        EXPECT_TRUE(legacyMetrics.list.get(attrName) == nullptr);
        EXPECT_TRUE(totalLegacyMetrics.list.get(attrName) == nullptr);
    }
};

TEST("require that the metric proton.diskusage is the sum of the documentDB diskusage metrics")
{
    MetricsEngine metrics_engine;

    DocumentDBMetricsCollection metrics1("type1", 1);
    DocumentDBMetricsCollection metrics2("type2", 1);
    metrics1.getLegacyMetrics().index.diskUsage.addValue(100);
    metrics2.getLegacyMetrics().index.diskUsage.addValue(1000);

    metrics_engine.addDocumentDBMetrics(metrics1);
    metrics_engine.addDocumentDBMetrics(metrics2);

    EXPECT_EQUAL(1100, metrics_engine.legacyRoot().diskUsage.getLongValue("value"));
}

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
