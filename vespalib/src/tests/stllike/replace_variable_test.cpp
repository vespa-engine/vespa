// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/replace_variable.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

TEST(ReplaceVariableTest, simple_usage)
{
    EXPECT_EQ("vv", replace_variable("x", "x", "vv"));
    EXPECT_EQ("f(vv)", replace_variable("f(x)", "x", "vv"));
    EXPECT_EQ("vv(f)", replace_variable("x(f)", "x", "vv"));
    EXPECT_EQ("3*vv", replace_variable("3*x", "x", "vv"));
    EXPECT_EQ("f(vv,vv,y)", replace_variable("f(x,x,y)", "x", "vv"));

    EXPECT_EQ("f(xx)", replace_variable("f(xx)", "x", "vv"));
    EXPECT_EQ("f(ax)", replace_variable("f(ax)", "x", "vv"));
    EXPECT_EQ("f(xa)", replace_variable("f(xa)", "x", "vv"));
    EXPECT_EQ("f(axa)", replace_variable("f(axa)", "x", "vv"));

    EXPECT_EQ("f(vv)", replace_variable("f(x_y)", "x_y", "vv"));
}

GTEST_MAIN_RUN_ALL_TESTS()
