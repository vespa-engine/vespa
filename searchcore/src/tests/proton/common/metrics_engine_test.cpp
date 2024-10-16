// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/metricset.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/index_metrics.h>
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
    IndexMetrics indexes;

    MetricsEngineTest();
    ~MetricsEngineTest() override;

    void set_attributes(std::vector<std::string> field_names) {
        engine.set_attributes(attributes, field_names);;
    }

    void set_index_fields(std::vector<std::string> field_names) {
        engine.set_index_fields(indexes, field_names);
    }

    size_t count_registered_metrics() const {
        return parent.getRegisteredMetrics().size();
    }

    bool has_attribute_metrics(const std::string &field_name) {
        return attributes.get_field_metrics_entry(field_name).get() != nullptr;
    }

    bool has_index_metrics(const std::string& field_name) {
        return indexes.get_field_metrics_entry(field_name).get() != nullptr;
    }
};

MetricsEngineTest::MetricsEngineTest()
    : ::testing::Test(),
      engine(),
      parent("parent"),
      attributes(&parent),
      indexes(&parent)
{
}

MetricsEngineTest::~MetricsEngineTest() = default;

TEST_F(MetricsEngineTest, require_that_attribute_metrics_can_be_added)
{
    EXPECT_EQ(0, count_registered_metrics());
    set_attributes({"foo"});
    EXPECT_EQ(1, count_registered_metrics());
    EXPECT_TRUE(has_attribute_metrics("foo"));
}

TEST_F(MetricsEngineTest, require_that_attribute_metrics_can_be_removed)
{
    EXPECT_EQ(0, count_registered_metrics());
    set_attributes({"foo"});
    EXPECT_EQ(1, count_registered_metrics());
    set_attributes({"foo", "bar"});
    EXPECT_EQ(2, count_registered_metrics());
    set_attributes({"bar"});
    EXPECT_EQ(1, count_registered_metrics());
    EXPECT_FALSE(has_attribute_metrics("foo"));
    EXPECT_TRUE(has_attribute_metrics("bar"));
}

TEST_F(MetricsEngineTest, require_that_all_attribute_metrics_can_be_cleaned)
{
    EXPECT_EQ(0, count_registered_metrics());
    set_attributes({"foo", "bar"});
    EXPECT_EQ(2, count_registered_metrics());
    set_attributes({});
    EXPECT_EQ(0, count_registered_metrics());
    EXPECT_FALSE(has_attribute_metrics("foo"));
    EXPECT_FALSE(has_attribute_metrics("bar"));
}

TEST_F(MetricsEngineTest, require_that_index_metrics_can_be_added)
{
    EXPECT_EQ(0, count_registered_metrics());
    set_index_fields({"foo"});
    EXPECT_EQ(1, count_registered_metrics());
    EXPECT_TRUE(has_index_metrics("foo"));
}

TEST_F(MetricsEngineTest, require_that_index_metrics_can_be_removed)
{
    EXPECT_EQ(0, count_registered_metrics());
    set_index_fields({"foo"});
    set_index_fields({"foo", "bar"});
    EXPECT_EQ(2, count_registered_metrics());
    set_index_fields({"bar"});
    EXPECT_EQ(1, count_registered_metrics());
    EXPECT_FALSE(has_index_metrics("foo"));
    EXPECT_TRUE(has_index_metrics("bar"));
}

TEST_F(MetricsEngineTest, require_that_all_index_metrics_can_be_cleaned)
{
    EXPECT_EQ(0, count_registered_metrics());
    set_index_fields({"foo", "bar"});
    EXPECT_EQ(2, count_registered_metrics());
    set_index_fields({});
    EXPECT_EQ(0, count_registered_metrics());
    EXPECT_FALSE(has_index_metrics("foo"));
    EXPECT_FALSE(has_index_metrics("bar"));
}

}
