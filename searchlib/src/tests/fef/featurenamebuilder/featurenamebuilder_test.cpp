// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("featurenamebuilder_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>

using namespace search::fef;

using B = FeatureNameBuilder;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("featurenamebuilder_test");

    // normal cases
    EXPECT_EQUAL(B().baseName("foo").buildName(), "foo");
    EXPECT_EQUAL(B().baseName("foo").output("out").buildName(), "foo.out");
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").buildName(), "foo(a,b)");
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").output("out").buildName(), "foo(a,b).out");

    // empty base = empty name
    EXPECT_EQUAL(B().baseName("").buildName(), "");
    EXPECT_EQUAL(B().baseName("").output("out").buildName(), "");
    EXPECT_EQUAL(B().baseName("").parameter("a").parameter("b").buildName(), "");
    EXPECT_EQUAL(B().baseName("").parameter("a").parameter("b").output("out").buildName(), "");

    // quoting
    EXPECT_EQUAL(B().baseName("foo").parameter("a,b").output("out").buildName(), "foo(\"a,b\").out");
    EXPECT_EQUAL(B().baseName("foo").parameter("a\\").output("out").buildName(), "foo(\"a\\\\\").out");
    EXPECT_EQUAL(B().baseName("foo").parameter("a)").output("out").buildName(), "foo(\"a)\").out");
    EXPECT_EQUAL(B().baseName("foo").parameter(" ").output("out").buildName(), "foo(\" \").out");
    EXPECT_EQUAL(B().baseName("foo").parameter("\"").output("out").buildName(), "foo(\"\\\"\").out");
    EXPECT_EQUAL(B().baseName("foo").parameter("\\\t\n\r\f\x15").output("out").buildName(), "foo(\"\\\\\\t\\n\\r\\f\\x15\").out");
    EXPECT_EQUAL(B().baseName("foo").parameter("\\\t\n\r\f\x20").output("out").buildName(), "foo(\"\\\\\\t\\n\\r\\f \").out");

    // empty parameters
    EXPECT_EQUAL(B().baseName("foo").parameter("").output("out").buildName(), "foo().out");
    EXPECT_EQUAL(B().baseName("foo").parameter("").parameter("").output("out").buildName(), "foo(,).out");
    EXPECT_EQUAL(B().baseName("foo").parameter("").parameter("").parameter("").output("out").buildName(), "foo(,,).out");
    EXPECT_EQUAL(B().baseName("foo").parameter("").parameter("x").parameter("").output("out").buildName(), "foo(,x,).out");

    // test change components
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").output("out").buildName(), "foo(a,b).out");
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").output("out").baseName("bar").buildName(), "bar(a,b).out");
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").output("out").clearParameters().buildName(), "foo.out");
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").output("out").clearParameters().parameter("x").buildName(), "foo(x).out");
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").output("out").output("").buildName(), "foo(a,b)");
    EXPECT_EQUAL(B().baseName("foo").parameter("a").parameter("b").output("out").output("len").buildName(), "foo(a,b).len");

    // test exact quote vs non-quote
    EXPECT_EQUAL(B().baseName("foo").parameter("a").buildName(), "foo(a)");
    EXPECT_EQUAL(B().baseName("foo").parameter(" a").buildName(), "foo(\" a\")");
    EXPECT_EQUAL(B().baseName("foo").parameter("a.out").buildName(), "foo(a.out)");
    EXPECT_EQUAL(B().baseName("foo").parameter(" a.out").buildName(), "foo(\" a.out\")");
    EXPECT_EQUAL(B().baseName("foo").parameter("bar(a,b)").buildName(), "foo(bar(a,b))");
    EXPECT_EQUAL(B().baseName("foo").parameter("bar(a, b)").buildName(), "foo(\"bar(a, b)\")");
    EXPECT_EQUAL(B().baseName("foo").parameter("bar(a,b).out").buildName(), "foo(bar(a,b).out)");
    EXPECT_EQUAL(B().baseName("foo").parameter("bar(a, b).out").buildName(), "foo(\"bar(a, b).out\")");

    // test non-exact quote vs non-quote
    EXPECT_EQUAL(B().baseName("foo").parameter(" \t\n\r\f", false).buildName(), "foo()");
    EXPECT_EQUAL(B().baseName("foo").parameter(" \t\n\r\fbar   ", false).buildName(), "foo(bar)");
    EXPECT_EQUAL(B().baseName("foo").parameter("   bar   ", false).buildName(), "foo(bar)");
    EXPECT_EQUAL(B().baseName("foo").parameter(" a b ", false).buildName(), "foo(\" a b \")");
    EXPECT_EQUAL(B().baseName("foo").parameter("a%", false).buildName(), "foo(\"a%\")");
    EXPECT_EQUAL(B().baseName("foo").parameter("foo\"\\", false).buildName(), "foo(\"foo\\\"\\\\\")");
    EXPECT_EQUAL(B().baseName("foo").parameter(" a . out ", false).buildName(), "foo(a.out)");
    EXPECT_EQUAL(B().baseName("foo").parameter(" bar ( a , b ) ", false).buildName(), "foo(bar(a,b))");
    EXPECT_EQUAL(B().baseName("foo").parameter(" bar ( a , b ) . out ", false).buildName(), "foo(bar(a,b).out)");
    EXPECT_EQUAL(B().baseName("foo").parameter(" bar ( a , b ) . out.2 ", false).buildName(), "foo(bar(a,b).out.2)");
    EXPECT_EQUAL(B().baseName("foo").parameter(" bar ( a , b ) . out . 2 ", false).buildName(), "foo(\" bar ( a , b ) . out . 2 \")");

    TEST_DONE();
}
