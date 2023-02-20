// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace metrics {

// Metric subclasses have the same constructor parameters, so we template
// our way from having to duplicate code. Templated GTest fixtures would be
// a far more elegant solution.
template <typename MetricImpl>
void
testMetricsGetDimensionsAsPartOfMangledNameImpl()
{
    MetricImpl m("test", {{"foo", "bar"}}, "description goes here");
    EXPECT_EQ(vespalib::string("test{foo:bar}"), m.getMangledName());
}

template <typename MetricImpl>
void
testMangledNameMayContainMultipleDimensionsImpl()
{
    MetricImpl m("test",
                {{"flarn", "yarn"}, {"foo", "bar"}},
                "description goes here");
    EXPECT_EQ(vespalib::string("test{flarn:yarn,foo:bar}"), m.getMangledName());
}

TEST(MetricTest, value_metrics_get_dimensions_as_part_of_mangled_name)
{
    testMetricsGetDimensionsAsPartOfMangledNameImpl<LongValueMetric>();
}

TEST(MetricTest, count_metrics_get_dimensions_as_part_of_mangled_name)
{
    testMetricsGetDimensionsAsPartOfMangledNameImpl<LongCountMetric>();
}

TEST(MetricTest, value_metric_mangled_name_may_contain_multiple_dimensions)
{
    testMangledNameMayContainMultipleDimensionsImpl<LongValueMetric>();
}

TEST(MetricTest, count_metric_mangled_name_may_contain_multiple_dimensions)
{
    testMangledNameMayContainMultipleDimensionsImpl<LongCountMetric>();
}

// Assuming the above tests pass, we simplify by not requiring all subclasses
// to be tested since propagation down to the base class has already been
// verified.
TEST(MetricTest, mangled_name_lists_dimensions_in_lexicographic_order)
{
    LongValueMetric m("test",
                      {{"xyz", "bar"}, {"abc", "foo"}, {"def", "baz"}},
                      "", nullptr);
    EXPECT_EQ(vespalib::string("test{abc:foo,def:baz,xyz:bar}"), m.getMangledName());
}

TEST(MetricTest, mangling_does_not_change_original_metric_name)
{
    LongValueMetric m("test", {{"foo", "bar"}}, "", nullptr);
    EXPECT_EQ(vespalib::string("test"), m.getName());
}

TEST(MetricTest, legacy_tags_do_not_create_mangled_name)
{
    LongValueMetric m("test", {{"foo"},{"bar"}}, "", nullptr);
    EXPECT_EQ(vespalib::string("test"), m.getName());
    EXPECT_EQ(vespalib::string("test"), m.getMangledName());
}

}

