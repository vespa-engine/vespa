// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <variant>
#include <string>

using namespace vespalib;

TEST(OverloadTest, visit_with_overload_works) {
    std::variant<std::string,int> a = 10;
    std::variant<std::string,int> b = "foo";
    std::visit(overload{[](int v){ EXPECT_EQ(v,10); },
                        [](const std::string &){ FAIL() << "invalid visit"; }}, a);
    std::visit(overload{[](int){ FAIL() << "invalid visit"; },
                        [](const std::string &v){ EXPECT_EQ(v, "foo"); }}, b);
}

GTEST_MAIN_RUN_ALL_TESTS()
