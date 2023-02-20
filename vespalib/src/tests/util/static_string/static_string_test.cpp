// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/static_string.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::StaticStringView;
using namespace vespalib::literals;

TEST(StaticStringViewTest, simple_usage) {
    auto value = "foo bar"_ssv;
    vespalib::string expect("foo bar");
    std::string expect_std("foo bar");
    static_assert(std::same_as<decltype(value),StaticStringView>);
    auto a_ref = value.ref();
    auto a_view = value.view();
    static_assert(std::same_as<decltype(a_ref),vespalib::stringref>);
    static_assert(std::same_as<decltype(a_view),std::string_view>);
    vespalib::stringref ref = value;
    std::string_view view = value;
    EXPECT_EQ(a_ref, expect);
    EXPECT_EQ(a_view, expect_std);
    EXPECT_EQ(ref, expect);
    EXPECT_EQ(view, expect_std);
    EXPECT_EQ(value.ref(), expect);
    EXPECT_EQ(value.view(), expect_std);
}

TEST(StaticStringViewTest, with_null_byte) {
    auto value = "foo\0bar"_ssv;
    std::string expect("foo\0bar", 7);
    EXPECT_EQ(value.view(), expect);
}

GTEST_MAIN_RUN_ALL_TESTS()
