// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("featurenamebuilder_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>

using namespace search::fef;

using B = FeatureNameBuilder;

TEST(FeatureNameBuilderTest, normal_cases) {
    EXPECT_EQ(B().baseName("foo").buildName(), "foo");
    EXPECT_EQ(B().baseName("foo").output("out").buildName(), "foo.out");
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").buildName(), "foo(a,b)");
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").output("out").buildName(), "foo(a,b).out");
}

TEST(FeatureNameBuilderTest, empty_base_gives_empty_name) {
    EXPECT_EQ(B().baseName("").buildName(), "");
    EXPECT_EQ(B().baseName("").output("out").buildName(), "");
    EXPECT_EQ(B().baseName("").parameter("a").parameter("b").buildName(), "");
    EXPECT_EQ(B().baseName("").parameter("a").parameter("b").output("out").buildName(), "");
}

TEST(FeatureNameBuilderTest, quoting) {
    EXPECT_EQ(B().baseName("foo").parameter("a,b").output("out").buildName(), "foo(\"a,b\").out");
    EXPECT_EQ(B().baseName("foo").parameter("a\\").output("out").buildName(), "foo(\"a\\\\\").out");
    EXPECT_EQ(B().baseName("foo").parameter("a)").output("out").buildName(), "foo(\"a)\").out");
    EXPECT_EQ(B().baseName("foo").parameter(" ").output("out").buildName(), "foo(\" \").out");
    EXPECT_EQ(B().baseName("foo").parameter("\"").output("out").buildName(), "foo(\"\\\"\").out");
    EXPECT_EQ(B().baseName("foo").parameter("\\\t\n\r\f\x15").output("out").buildName(), "foo(\"\\\\\\t\\n\\r\\f\\x15\").out");
    EXPECT_EQ(B().baseName("foo").parameter("\\\t\n\r\f\x20").output("out").buildName(), "foo(\"\\\\\\t\\n\\r\\f \").out");
}

TEST(FeatureNameBuilderTest, empty_parameters) {
    EXPECT_EQ(B().baseName("foo").parameter("").output("out").buildName(), "foo().out");
    EXPECT_EQ(B().baseName("foo").parameter("").parameter("").output("out").buildName(), "foo(,).out");
    EXPECT_EQ(B().baseName("foo").parameter("").parameter("").parameter("").output("out").buildName(), "foo(,,).out");
    EXPECT_EQ(B().baseName("foo").parameter("").parameter("x").parameter("").output("out").buildName(), "foo(,x,).out");
}

TEST(FeatureNameBuilderTest, change_components) {
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").output("out").buildName(), "foo(a,b).out");
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").output("out").baseName("bar").buildName(), "bar(a,b).out");
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").output("out").clearParameters().buildName(), "foo.out");
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").output("out").clearParameters().parameter("x").buildName(), "foo(x).out");
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").output("out").output("").buildName(), "foo(a,b)");
    EXPECT_EQ(B().baseName("foo").parameter("a").parameter("b").output("out").output("len").buildName(), "foo(a,b).len");
}

TEST(FeatureNameBuilderTest, exact_quote_vs_non_quote) {
    EXPECT_EQ(B().baseName("foo").parameter("a").buildName(), "foo(a)");
    EXPECT_EQ(B().baseName("foo").parameter(" a").buildName(), "foo(\" a\")");
    EXPECT_EQ(B().baseName("foo").parameter("a.out").buildName(), "foo(a.out)");
    EXPECT_EQ(B().baseName("foo").parameter(" a.out").buildName(), "foo(\" a.out\")");
    EXPECT_EQ(B().baseName("foo").parameter("bar(a,b)").buildName(), "foo(bar(a,b))");
    EXPECT_EQ(B().baseName("foo").parameter("bar(a, b)").buildName(), "foo(\"bar(a, b)\")");
    EXPECT_EQ(B().baseName("foo").parameter("bar(a,b).out").buildName(), "foo(bar(a,b).out)");
    EXPECT_EQ(B().baseName("foo").parameter("bar(a, b).out").buildName(), "foo(\"bar(a, b).out\")");
}

TEST(FeatureNameBuilderTest, non_exact_quote_vs_non_quote) {
    EXPECT_EQ(B().baseName("foo").parameter(" \t\n\r\f", false).buildName(), "foo()");
    EXPECT_EQ(B().baseName("foo").parameter(" \t\n\r\fbar   ", false).buildName(), "foo(bar)");
    EXPECT_EQ(B().baseName("foo").parameter("   bar   ", false).buildName(), "foo(bar)");
    EXPECT_EQ(B().baseName("foo").parameter(" a b ", false).buildName(), "foo(\" a b \")");
    EXPECT_EQ(B().baseName("foo").parameter("a%", false).buildName(), "foo(\"a%\")");
    EXPECT_EQ(B().baseName("foo").parameter("foo\"\\", false).buildName(), "foo(\"foo\\\"\\\\\")");
    EXPECT_EQ(B().baseName("foo").parameter(" a . out ", false).buildName(), "foo(a.out)");
    EXPECT_EQ(B().baseName("foo").parameter(" bar ( a , b ) ", false).buildName(), "foo(bar(a,b))");
    EXPECT_EQ(B().baseName("foo").parameter(" bar ( a , b ) . out ", false).buildName(), "foo(bar(a,b).out)");
    EXPECT_EQ(B().baseName("foo").parameter(" bar ( a , b ) . out.2 ", false).buildName(), "foo(bar(a,b).out.2)");
    EXPECT_EQ(B().baseName("foo").parameter(" bar ( a , b ) . out . 2 ", false).buildName(), "foo(\" bar ( a , b ) . out . 2 \")");
}

GTEST_MAIN_RUN_ALL_TESTS()
