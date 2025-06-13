// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/approx.h>
#include <limits>
#include <cfloat>
#include <cmath>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::approx_equal;

TEST(ApproxTest, require_that_equal_numbers_are_also_approximately_equal) {
    EXPECT_TRUE(approx_equal(2.0, 2.0));
    EXPECT_TRUE(approx_equal(1.0, 1.0));
    EXPECT_TRUE(approx_equal(0.5, 0.5));
    EXPECT_TRUE(approx_equal(0.0, 0.0));
    EXPECT_TRUE(approx_equal(0.0, -0.0));
    EXPECT_TRUE(approx_equal(-0.0, -0.0));
    EXPECT_TRUE(approx_equal(-0.5, -0.5));
    EXPECT_TRUE(approx_equal(-1.0, -1.0));
    EXPECT_TRUE(approx_equal(-2.0, -2.0));
    EXPECT_TRUE(approx_equal(1e10, 1e10));
    EXPECT_TRUE(approx_equal(1e20, 1e20));
    EXPECT_TRUE(approx_equal(1e30, 1e30));
    EXPECT_TRUE(approx_equal(-1e10, -1e10));
    EXPECT_TRUE(approx_equal(-1e20, -1e20));
    EXPECT_TRUE(approx_equal(-1e30, -1e30));
    EXPECT_TRUE(approx_equal(std::numeric_limits<double>::infinity(),
                             std::numeric_limits<double>::infinity()));
}

TEST(ApproxTest, require_that_very_different_numbers_are_not_approximately_equal) {
    EXPECT_FALSE(approx_equal(2.0, 1.0));
    EXPECT_FALSE(approx_equal(1.0, 0.0));
    EXPECT_FALSE(approx_equal(0.5, 0.25));
    EXPECT_FALSE(approx_equal(0.0, -0.07));
    EXPECT_FALSE(approx_equal(-0.0, -0.5));
    EXPECT_FALSE(approx_equal(-0.5, -1.0));
    EXPECT_FALSE(approx_equal(-1.0, -2.0));
    EXPECT_FALSE(approx_equal(1e30, 1e31));
    EXPECT_FALSE(approx_equal(-1e30, -1e31));
}

TEST(ApproxTest, require_that_numbers_with_very_small_differences_are_approximately_equal) {
    double epsilon = FLT_EPSILON * 0.3;
    double larger = 1.0 + epsilon;
    double smaller = 1.0 - epsilon;
    for (double d: { 1e40, 1e20, 1e10, 2.0, 1.0, 0.5, 1e-20 }) {
        SCOPED_TRACE(vespalib::make_string("d = %.17g", d));
        EXPECT_TRUE(approx_equal(d, d * larger));
        EXPECT_TRUE(approx_equal(d, d * smaller));
        EXPECT_TRUE(approx_equal(d * larger, d));
        EXPECT_TRUE(approx_equal(d * smaller, d));
        EXPECT_TRUE(approx_equal(d * smaller, d * larger));
        EXPECT_TRUE(approx_equal(d * larger, d * smaller));
        double nd = -d;
        EXPECT_TRUE(approx_equal(nd, nd * larger));
        EXPECT_TRUE(approx_equal(nd, nd * smaller));
        EXPECT_TRUE(approx_equal(nd * larger, nd));
        EXPECT_TRUE(approx_equal(nd * smaller, nd));
        EXPECT_TRUE(approx_equal(nd * smaller, nd * larger));
        EXPECT_TRUE(approx_equal(nd * larger, nd * smaller));
    }
}

TEST(ApproxTest, require_that_numbers_with_slightly_larger_differences_are_not_approximately_equal) {
    double epsilon = FLT_EPSILON * 1.5;
    double larger = 1.0 + epsilon;
    double smaller = 1.0 - epsilon;
    for (double d: { 1e40, 1e20, 1e10, 2.0, 1.0, 0.5, 1e-20 }) {
        SCOPED_TRACE(vespalib::make_string("d = %.17g", d));
        EXPECT_FALSE(approx_equal(d, d * larger));
        EXPECT_FALSE(approx_equal(d, d * smaller));
        EXPECT_FALSE(approx_equal(d * larger, d));
        EXPECT_FALSE(approx_equal(d * smaller, d));
        EXPECT_FALSE(approx_equal(d * smaller, d * larger));
        EXPECT_FALSE(approx_equal(d * larger, d * smaller));
        double nd = -d;
        EXPECT_FALSE(approx_equal(nd, nd * larger));
        EXPECT_FALSE(approx_equal(nd, nd * smaller));
        EXPECT_FALSE(approx_equal(nd * larger, nd));
        EXPECT_FALSE(approx_equal(nd * smaller, nd));
        EXPECT_FALSE(approx_equal(nd * smaller, nd * larger));
        EXPECT_FALSE(approx_equal(nd * larger, nd * smaller));
    }
}

TEST(ApproxTest, require_that_specific_numbers_with_almost_2_ULP_differences_are_approximately_equal) {
    double base = 0.25111f;
    double epsilon = std::nextafterf(base, 1.0) - base;
    double larger = base + epsilon*1.499;
    double smaller = base - epsilon*0.499;
    EXPECT_TRUE(approx_equal(larger, smaller));
    EXPECT_TRUE(approx_equal(smaller, larger));
    larger = base + epsilon*1.501;
    smaller = base - epsilon*0.499;
    EXPECT_FALSE(approx_equal(larger, smaller));
    EXPECT_FALSE(approx_equal(smaller, larger));
    larger = base + epsilon*1.499;
    smaller = base - epsilon*0.501;
    EXPECT_FALSE(approx_equal(larger, smaller));
    EXPECT_FALSE(approx_equal(smaller, larger));
}

GTEST_MAIN_RUN_ALL_TESTS()
