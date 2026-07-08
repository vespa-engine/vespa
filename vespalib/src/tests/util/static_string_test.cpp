// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/static_string.h>

using vespalib::StaticStringView;
using namespace vespalib::literals;

TEST(StaticStringViewTest, simple_usage) {
    auto        value = "foo bar"_ssv;
    std::string expect("foo bar");
    static_assert(std::same_as<decltype(value), StaticStringView>);
    auto view = value.view();
    static_assert(std::same_as<decltype(view), std::string_view>);
    EXPECT_EQ(view, expect);
}

TEST(StaticStringViewTest, with_null_byte) {
    auto        value = "foo\0bar"_ssv;
    std::string expect("foo\0bar", 7);
    EXPECT_EQ(value.view(), expect);
}
