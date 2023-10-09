// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/replace_variable.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

TEST(ReplaceVariableTest, simple_usage)
{
    // replace one variable
    EXPECT_EQ("vv", replace_variable("x", "x", "vv"));
    EXPECT_EQ("f(vv)", replace_variable("f(x)", "x", "vv"));
    EXPECT_EQ("f(vv)", replace_variable("f(myvariablename)", "myvariablename", "vv"));
    EXPECT_EQ("vv(f)", replace_variable("x(f)", "x", "vv"));
    EXPECT_EQ("3*vv", replace_variable("3*x", "x", "vv"));

    // replace variable multiple times
    EXPECT_EQ("vv(vv,vv*vv)+vv", replace_variable("x(x,x*x)+x", "x", "vv"));

    // do not replace variable when substring of a word
    EXPECT_EQ("f(xx)", replace_variable("f(xx)", "x", "vv"));
    EXPECT_EQ("f(ax)", replace_variable("f(ax)", "x", "vv"));
    EXPECT_EQ("f(xa)", replace_variable("f(xa)", "x", "vv"));
    EXPECT_EQ("f(axa)", replace_variable("f(axa)", "x", "vv"));

    // variable names can contain underscore '_'
    EXPECT_EQ("f(vv)", replace_variable("f(x_y)", "x_y", "vv"));
}

GTEST_MAIN_RUN_ALL_TESTS()
