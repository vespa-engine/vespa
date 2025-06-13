// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::make_string;
using vespalib::make_string_short::fmt;

TEST(FmtTest, test_that_make_string_formats_as_one_can_expect)
{
    int i=7;
    int j=0x666;
    const char *s = "a test ";

    std::string foo = make_string("%d/%x", i, j);
    std::string bar = make_string("%d/%x", i, j).c_str();

    std::string tst("7/666");

    EXPECT_TRUE(tst == foo);
    EXPECT_TRUE(tst == bar);

    EXPECT_TRUE(tst == make_string("%d/%x", i, j));

    tst = "a test ";
    EXPECT_TRUE(tst == make_string("%s", s));

    tst = "a t";
    EXPECT_TRUE(tst == make_string("%.3s", s));

    const char *p = "really really really really "
              "very very very very very "
              "extremely extremely extremely extremely "
              "very very very very very "
              "really really really really "
              "insanely insanely insanely insanely "
              "hugely hugely hugely hugely "
              "bloated fat long string";
    tst = p;
    EXPECT_TRUE(tst == make_string("%s", p));
}

TEST(FmtTest, require_that_short_form_make_string_can_be_used) {
    EXPECT_EQ(fmt("format: %d", 123), std::string("format: 123"));
}

GTEST_MAIN_RUN_ALL_TESTS()
