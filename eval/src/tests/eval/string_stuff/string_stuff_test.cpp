// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/string_stuff.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <limits>

using namespace vespalib::eval;

constexpr size_t bad = std::numeric_limits<size_t>::max();

struct EatCtx {
    std::string eaten;
    void eat(char c) { eaten.push_back(c); }
};

TEST(StringStuffTest, require_that_comma_tracker_adds_commas_between_items) {
    CommaTracker tracker;
    std::string  str;
    for (const char* item : {"a", "b", "c"}) {
        tracker.maybe_add_comma(str);
        str += item;
    }
    EXPECT_EQ(str, "a,b,c");
}

TEST(StringStuffTest, require_that_comma_tracker_reports_whether_comma_was_added) {
    CommaTracker tracker;
    std::string  str;
    EXPECT_FALSE(tracker.maybe_add_comma(str));
    EXPECT_EQ(str, "");
    EXPECT_TRUE(tracker.maybe_add_comma(str));
    EXPECT_EQ(str, ",");
}

TEST(StringStuffTest, require_that_comma_tracker_can_start_after_first_item) {
    CommaTracker tracker(false);
    std::string  str;
    EXPECT_TRUE(tracker.maybe_add_comma(str));
    EXPECT_EQ(str, ",");
}

TEST(StringStuffTest, require_that_comma_tracker_eats_commas_between_items) {
    CommaTracker tracker;
    EatCtx       ctx;
    EXPECT_FALSE(tracker.maybe_eat_comma(ctx));
    EXPECT_EQ(ctx.eaten, "");
    EXPECT_TRUE(tracker.maybe_eat_comma(ctx));
    EXPECT_TRUE(tracker.maybe_eat_comma(ctx));
    EXPECT_EQ(ctx.eaten, ",,");
}

TEST(StringStuffTest, require_that_plain_strings_are_quoted) {
    EXPECT_EQ(as_quoted_string(""), "\"\"");
    EXPECT_EQ(as_quoted_string("foo bar"), "\"foo bar\"");
    EXPECT_EQ(as_quoted_string(" ~"), "\" ~\""); // printable range limits (32 and 126)
}

TEST(StringStuffTest, require_that_special_characters_are_escaped_when_quoting) {
    EXPECT_EQ(as_quoted_string("\\"), "\"\\\\\"");
    EXPECT_EQ(as_quoted_string("\""), "\"\\\"\"");
    EXPECT_EQ(as_quoted_string("\t\n\r\f"), "\"\\t\\n\\r\\f\"");
}

TEST(StringStuffTest, require_that_non_printable_characters_are_hex_escaped_when_quoting) {
    EXPECT_EQ(as_quoted_string(std::string(1, '\0')), "\"\\x00\"");
    EXPECT_EQ(as_quoted_string("\x01\x1f\x7f"), "\"\\x01\\x1f\\x7f\"");
    EXPECT_EQ(as_quoted_string("\x80\xff"), "\"\\x80\\xff\""); // high bit set (signed char)
}

TEST(StringStuffTest, require_that_numbers_are_converted) {
    EXPECT_TRUE(is_number("0"));
    EXPECT_TRUE(is_number("007"));
    EXPECT_TRUE(is_number("4294967297"));
    EXPECT_EQ(as_number("0"), 0u);
    EXPECT_EQ(as_number("007"), 7u);
    EXPECT_EQ(as_number("4294967297"), 4294967297u); // does not fit in 32 bits
}

TEST(StringStuffTest, require_that_numbers_too_large_for_size_t_are_converted_to_bad) {
    std::string max_str = std::to_string(bad);
    EXPECT_EQ(max_str, "18446744073709551615");
    EXPECT_EQ(as_number(max_str), bad); // largest value; indistinguishable from bad by design
    EXPECT_TRUE(is_number("18446744073709551616"));
    EXPECT_EQ(as_number("18446744073709551616"), bad); // first value that overflows
    EXPECT_TRUE(is_number("99999999999999999999"));
    EXPECT_EQ(as_number("99999999999999999999"), bad);
}

TEST(StringStuffTest, require_that_non_numbers_are_rejected) {
    for (const char* str : {"", " 12", "12 ", "12abc", "-1", "+1"}) {
        EXPECT_FALSE(is_number(str)) << "'" << str << "'";
        EXPECT_EQ(as_number(str), bad) << "'" << str << "'";
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
