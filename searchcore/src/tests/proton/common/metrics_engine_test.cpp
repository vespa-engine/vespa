// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/metricset.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/metrics_engine.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace proton;

namespace {

struct DummyMetricSet : public metrics::MetricSet {
    DummyMetricSet(const std::string &name) : metrics::MetricSet(name, {}, "", nullptr) {}
};

class MetricsEngineTest : public ::testing::Test {
protected:
    MetricsEngine engine;
    DummyMetricSet parent;
    AttributeMetrics attributes;

    MetricsEngineTest();
    ~MetricsEngineTest() override;

    void addAttribute(const std::string &attrName) {
        engine.addAttribute(attributes, attrName);
    }

    void removeAttribute(const std::string &attrName) {
        engine.removeAttribute(attributes, attrName);
    }

    void cleanAttributes() {
        engine.cleanAttributes(attributes);
    }

    size_t count_registered_metrics() const {
        return parent.getRegisteredMetrics().size();
    }

    bool has_attribute_metrics(const std::string &field_name) {
        return attributes.get(field_name).get() != nullptr;
    }
};

MetricsEngineTest::MetricsEngineTest()
    : ::testing::Test(),
      engine(),
      parent("parent"),
      attributes(&parent)
{
}

MetricsEngineTest::~MetricsEngineTest() = default;

TEST_F(MetricsEngineTest, require_that_attribute_metrics_can_be_added)
{
    EXPECT_EQ(0, count_registered_metrics());
    addAttribute("foo");
    EXPECT_EQ(1, count_registered_metrics());
    EXPECT_TRUE(has_attribute_metrics("foo"));
}

TEST_F(MetricsEngineTest, require_that_attribute_metrics_can_be_removed)
{
    EXPECT_EQ(0, count_registered_metrics());
    addAttribute("foo");
    addAttribute("bar");
    EXPECT_EQ(2, count_registered_metrics());
    removeAttribute("foo");
    EXPECT_EQ(1, count_registered_metrics());
    EXPECT_FALSE(has_attribute_metrics("foo"));
    EXPECT_TRUE(has_attribute_metrics("bar"));
}

TEST_F(MetricsEngineTest, require_that_all_attribute_metrics_can_be_cleaned)
{
    EXPECT_EQ(0, count_registered_metrics());
    addAttribute("foo");
    addAttribute("bar");
    EXPECT_EQ(2, count_registered_metrics());
    cleanAttributes();
    EXPECT_EQ(0, count_registered_metrics());
    EXPECT_FALSE(has_attribute_metrics("foo"));
    EXPECT_FALSE(has_attribute_metrics("bar"));
}

}
