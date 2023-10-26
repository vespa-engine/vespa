// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/approx.h>
#include <limits>
#include <cfloat>
#include <cmath>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::approx_equal;

TEST("require that equal numbers are also approximately equal") {
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

TEST("require that very different numbers are not approximately equal") {
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

TEST("require that numbers with very small differences are approximately equal") {
    double epsilon = FLT_EPSILON * 0.3;
    double larger = 1.0 + epsilon;
    double smaller = 1.0 - epsilon;
    for (double d: { 1e40, 1e20, 1e10, 2.0, 1.0, 0.5, 1e-20 }) {
        TEST_STATE(vespalib::make_string("d = %.17g", d).c_str());
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

TEST("require that numbers with slightly larger differences are not approximately equal") {
    double epsilon = FLT_EPSILON * 1.5;
    double larger = 1.0 + epsilon;
    double smaller = 1.0 - epsilon;
    for (double d: { 1e40, 1e20, 1e10, 2.0, 1.0, 0.5, 1e-20 }) {
        TEST_STATE(vespalib::make_string("d = %.17g", d).c_str());
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

TEST("require that specific numbers with almost 2 ULP differences are approximately equal") {
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

TEST_MAIN() { TEST_RUN_ALL(); }
