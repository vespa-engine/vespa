// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/countmetric.h>

namespace metrics {

struct MetricTest : public CppUnit::TestFixture
{
    template <typename MetricImpl>
    void testMetricsGetDimensionsAsPartOfMangledNameImpl();
    template <typename MetricImpl>
    void testMangledNameMayContainMultipleDimensionsImpl();

    void valueMetricsGetDimensionsAsPartOfMangledName();
    void countMetricsGetDimensionsAsPartOfMangledName();
    void valueMetricMangledNameMayContainMultipleDimensions();
    void countMetricMangledNameMayContainMultipleDimensions();
    void mangledNameListsDimensionsInLexicographicOrder();
    void manglingDoesNotChangeOriginalMetricName();
    void legacyTagsDoNotCreateMangledName();

    CPPUNIT_TEST_SUITE(MetricTest);
    CPPUNIT_TEST(valueMetricsGetDimensionsAsPartOfMangledName);
    CPPUNIT_TEST(countMetricsGetDimensionsAsPartOfMangledName);
    CPPUNIT_TEST(valueMetricMangledNameMayContainMultipleDimensions);
    CPPUNIT_TEST(countMetricMangledNameMayContainMultipleDimensions);
    CPPUNIT_TEST(mangledNameListsDimensionsInLexicographicOrder);
    CPPUNIT_TEST(manglingDoesNotChangeOriginalMetricName);
    CPPUNIT_TEST(legacyTagsDoNotCreateMangledName);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MetricTest);

// Metric subclasses have the same constructor parameters, so we template
// our way from having to duplicate code. Templated GTest fixtures would be
// a far more elegant solution.
template <typename MetricImpl>
void
MetricTest::testMetricsGetDimensionsAsPartOfMangledNameImpl()
{
    MetricImpl m("test", {{"foo", "bar"}}, "description goes here");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("test{foo:bar}"), m.getMangledName());
}

template <typename MetricImpl>
void
MetricTest::testMangledNameMayContainMultipleDimensionsImpl()
{
    MetricImpl m("test",
                {{"flarn", "yarn"}, {"foo", "bar"}},
                "description goes here");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("test{flarn:yarn,foo:bar}"),
                         m.getMangledName());
}

void
MetricTest::valueMetricsGetDimensionsAsPartOfMangledName()
{
    testMetricsGetDimensionsAsPartOfMangledNameImpl<LongValueMetric>();
}

void
MetricTest::countMetricsGetDimensionsAsPartOfMangledName()
{
    testMetricsGetDimensionsAsPartOfMangledNameImpl<LongCountMetric>();
}

void
MetricTest::valueMetricMangledNameMayContainMultipleDimensions()
{
    testMangledNameMayContainMultipleDimensionsImpl<LongValueMetric>();
}

void
MetricTest::countMetricMangledNameMayContainMultipleDimensions()
{
    testMangledNameMayContainMultipleDimensionsImpl<LongCountMetric>();
}

// Assuming the above tests pass, we simplify by not requiring all subclasses
// to be tested since propagation down to the base class has already been
// verified.
void
MetricTest::mangledNameListsDimensionsInLexicographicOrder()
{
    LongValueMetric m("test",
                      {{"xyz", "bar"}, {"abc", "foo"}, {"def", "baz"}},
                      "");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("test{abc:foo,def:baz,xyz:bar}"),
                         m.getMangledName());
}

void
MetricTest::manglingDoesNotChangeOriginalMetricName()
{
    LongValueMetric m("test", {{"foo", "bar"}}, "");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("test"), m.getName());
}

void
MetricTest::legacyTagsDoNotCreateMangledName()
{
    LongValueMetric m("test", {{"foo"},{"bar"}}, "");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("test"), m.getName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("test"), m.getMangledName());
}

} // metrics

