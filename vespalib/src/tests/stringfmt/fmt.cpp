// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/testkit/test_kit.h>

using vespalib::make_string;
using vespalib::make_string_short::fmt;

TEST("test that make_string formats as one can expect.")
{
    int i=7;
    int j=0x666;
    const char *s = "a test ";

    std::string foo = make_string("%d/%x", i, j);
    std::string bar = make_string("%d/%x", i, j).c_str();

    vespalib::string tst("7/666");

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

TEST("require that short form make string can be used") {
    EXPECT_EQUAL(fmt("format: %d", 123), vespalib::string("format: 123"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
