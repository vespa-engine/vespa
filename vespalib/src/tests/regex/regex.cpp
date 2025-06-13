// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/regex/regex.h>
#include <vespa/vespalib/util/regexp.h>
#include <string>

using namespace vespalib;

TEST(RegExTest, require_that_prefix_detection_works) {
    EXPECT_EQ("", RegexpUtil::get_prefix(""));
    EXPECT_EQ("", RegexpUtil::get_prefix("foo"));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo"));
    EXPECT_EQ("", RegexpUtil::get_prefix("^foo|bar"));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo$"));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo[a-z]"));
    EXPECT_EQ("fo", RegexpUtil::get_prefix("^foo{0,1}"));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo."));
    EXPECT_EQ("fo", RegexpUtil::get_prefix("^foo*"));
    EXPECT_EQ("fo", RegexpUtil::get_prefix("^foo?"));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo+"));
}

TEST(RegExTest, require_that_prefix_detection_sometimes_underestimates_the_prefix_size) {
    EXPECT_EQ("", RegexpUtil::get_prefix("^^foo"));
    EXPECT_EQ("", RegexpUtil::get_prefix("^foo(bar|baz)"));
    EXPECT_EQ("fo", RegexpUtil::get_prefix("^foo{1,2}"));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo\\."));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo(bar)"));
    EXPECT_EQ("", RegexpUtil::get_prefix("(^foo)"));
    EXPECT_EQ("", RegexpUtil::get_prefix("^(foo)"));
    EXPECT_EQ("foo", RegexpUtil::get_prefix("^foo[a]"));
    EXPECT_EQ("", RegexpUtil::get_prefix("^foo|^foobar"));
}

const std::string special("^|()[]{}.*?+\\$");

struct ExprFixture {
    std::vector<std::string> expressions;
    ExprFixture() {
        expressions.push_back(special);
        for (char c: special) {
            expressions.emplace_back(std::string(&c, 1));
        }
        expressions.emplace_back("abc");
        expressions.emplace_back("[:digit:]");
    }
};

TEST(RegExTest, require_that_regexp_can_be_made_from_suffix_string) {
    ExprFixture f1;
    for (const auto& str: f1.expressions) {
        auto re = Regex::from_pattern(std::string(RegexpUtil::make_from_suffix(str)));
        ASSERT_TRUE(re.parsed_ok());

        EXPECT_TRUE(re.partial_match(str));
        EXPECT_FALSE(re.partial_match(str + "foo"));
        EXPECT_TRUE(re.partial_match("foo" + str));
        EXPECT_FALSE(re.partial_match("foo" + str + "bar"));
    }
}

TEST(RegExTest, require_that_regexp_can_be_made_from_substring_string) {
    ExprFixture f1;
    for (const auto& str: f1.expressions) {
        auto re = Regex::from_pattern(std::string(RegexpUtil::make_from_substring(str)));
        ASSERT_TRUE(re.parsed_ok());

        EXPECT_TRUE(re.partial_match(str));
        EXPECT_TRUE(re.partial_match(str + "foo"));
        EXPECT_TRUE(re.partial_match("foo" + str));
        EXPECT_TRUE(re.partial_match("foo" + str + "bar"));
    }
}

TEST(RegExTest, full_match_requires_expression_to_match_entire_input_string) {
    std::string pattern = "[Aa][Bb][Cc]";
    auto re = Regex::from_pattern(pattern);
    ASSERT_TRUE(re.parsed_ok());

    EXPECT_TRUE(re.full_match("abc"));
    EXPECT_TRUE(re.full_match("ABC"));
    EXPECT_FALSE(re.full_match("abcd"));
    EXPECT_FALSE(re.full_match("aabc"));
    EXPECT_FALSE(re.full_match("aabcc"));

    EXPECT_TRUE(Regex::full_match("abc", pattern));
    EXPECT_TRUE(Regex::full_match("ABC", pattern));
    EXPECT_FALSE(Regex::full_match("abcd", pattern));
    EXPECT_FALSE(Regex::full_match("aabc", pattern));
    EXPECT_FALSE(Regex::full_match("aabcc", pattern));
}

TEST(RegExTest, partial_match_requires_expression_to_match_substring_of_input_string) {
    std::string pattern = "[Aa][Bb][Cc]";
    auto re = Regex::from_pattern(pattern);
    ASSERT_TRUE(re.parsed_ok());

    EXPECT_TRUE(re.partial_match("abc"));
    EXPECT_TRUE(re.partial_match("ABC"));
    EXPECT_TRUE(re.partial_match("abcd"));
    EXPECT_TRUE(re.partial_match("aabc"));
    EXPECT_TRUE(re.partial_match("aabcc"));
    EXPECT_FALSE(re.partial_match("abd"));

    EXPECT_TRUE(Regex::partial_match("abc", pattern));
    EXPECT_TRUE(Regex::partial_match("ABC", pattern));
    EXPECT_TRUE(Regex::partial_match("abcd", pattern));
    EXPECT_TRUE(Regex::partial_match("aabc", pattern));
    EXPECT_TRUE(Regex::partial_match("aabcc", pattern));
    EXPECT_FALSE(Regex::partial_match("abd", pattern));
}

TEST(RegExTest, partial_match_can_be_explicitly_anchored) {
    EXPECT_TRUE(Regex::partial_match("abcc", "^abc"));
    EXPECT_FALSE(Regex::partial_match("aabc", "^abc"));
    EXPECT_TRUE(Regex::partial_match("aabc", "abc$"));
    EXPECT_FALSE(Regex::partial_match("abcc", "abc$"));
    EXPECT_TRUE(Regex::partial_match("abc", "^abc$"));
    EXPECT_FALSE(Regex::partial_match("aabc", "^abc$"));
    EXPECT_FALSE(Regex::partial_match("abcc", "^abc$"));
}

TEST(RegExTest, regex_instance_returns_parsed_ok_eq_false_upon_parse_failure) {
    auto re = Regex::from_pattern("[a-z"); // Unterminated set
    EXPECT_FALSE(re.parsed_ok());
}

TEST(RegExTest, regex_that_has_failed_parsing_immediately_returns_false_for_matches) {
    auto re = Regex::from_pattern("[a-z");
    EXPECT_FALSE(re.parsed_ok());
    EXPECT_FALSE(re.partial_match("a"));
    EXPECT_FALSE(re.full_match("b"));
}

TEST(RegExTest, can_create_case_insensitive_regex_matcher) {
    auto re = Regex::from_pattern("hello", Regex::Options::IgnoreCase);
    ASSERT_TRUE(re.parsed_ok());
    EXPECT_TRUE(re.partial_match("HelLo world"));
    EXPECT_TRUE(re.full_match("HELLO"));
}

TEST(RegExTest, regex_is_case_sensitive_by_default) {
    auto re = Regex::from_pattern("hello");
    ASSERT_TRUE(re.valid());
    ASSERT_TRUE(re.parsed_ok());
    EXPECT_FALSE(re.partial_match("HelLo world"));
    EXPECT_FALSE(re.full_match("HELLO"));
}

TEST(RegExTest, that_default_constructed_regex_is_invalid) {
    Regex dummy;
    ASSERT_FALSE(dummy.valid());
}

TEST(RegExTest, can_extract_min_max_prefix_range_from_anchored_regex) {
    auto min_max = Regex::from_pattern("^.*").possible_anchored_match_prefix_range();
    EXPECT_EQ(min_max.first,  "");
    EXPECT_GE(min_max.second, "\xf4\x8f\xbf\xc0"); // Highest possible Unicode char (U+10FFFF) as UTF-8, plus 1

    min_max = Regex::from_pattern("^hello").possible_anchored_match_prefix_range();
    EXPECT_EQ(min_max.first,  "hello");
    EXPECT_EQ(min_max.second, "hello");

    min_max = Regex::from_pattern("^hello|^world").possible_anchored_match_prefix_range();
    EXPECT_EQ(min_max.first,  "hello");
    EXPECT_EQ(min_max.second, "world");

    min_max = Regex::from_pattern("(^hello|^world|^zoidberg)").possible_anchored_match_prefix_range();
    EXPECT_EQ(min_max.first,  "hello");
    EXPECT_EQ(min_max.second, "zoidberg");

    min_max = Regex::from_pattern("^hello (foo|bar|zoo)").possible_anchored_match_prefix_range();
    EXPECT_EQ(min_max.first,  "hello bar");
    EXPECT_EQ(min_max.second, "hello zoo");

    min_max = Regex::from_pattern("^(hello|world)+").possible_anchored_match_prefix_range();
    EXPECT_EQ(min_max.first,  "hello");
    EXPECT_EQ(min_max.second, "worldwp");

    // Bad regex; no range
    min_max = Regex::from_pattern("*hello").possible_anchored_match_prefix_range();
    EXPECT_EQ(min_max.first,  "");
    EXPECT_EQ(min_max.second, "");
}

GTEST_MAIN_RUN_ALL_TESTS()
