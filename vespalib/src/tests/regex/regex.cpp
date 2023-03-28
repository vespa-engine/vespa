// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/regex/regex.h>
#include <vespa/vespalib/util/regexp.h>
#include <string>

using namespace vespalib;

TEST("require that prefix detection works") {
    EXPECT_EQUAL("", RegexpUtil::get_prefix(""));
    EXPECT_EQUAL("", RegexpUtil::get_prefix("foo"));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo"));
    EXPECT_EQUAL("", RegexpUtil::get_prefix("^foo|bar"));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo$"));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo[a-z]"));
    EXPECT_EQUAL("fo", RegexpUtil::get_prefix("^foo{0,1}"));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo."));
    EXPECT_EQUAL("fo", RegexpUtil::get_prefix("^foo*"));
    EXPECT_EQUAL("fo", RegexpUtil::get_prefix("^foo?"));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo+"));
}

TEST("require that prefix detection sometimes underestimates the prefix size") {
    EXPECT_EQUAL("", RegexpUtil::get_prefix("^^foo"));
    EXPECT_EQUAL("", RegexpUtil::get_prefix("^foo(bar|baz)"));
    EXPECT_EQUAL("fo", RegexpUtil::get_prefix("^foo{1,2}"));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo\\."));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo(bar)"));
    EXPECT_EQUAL("", RegexpUtil::get_prefix("(^foo)"));
    EXPECT_EQUAL("", RegexpUtil::get_prefix("^(foo)"));
    EXPECT_EQUAL("foo", RegexpUtil::get_prefix("^foo[a]"));
    EXPECT_EQUAL("", RegexpUtil::get_prefix("^foo|^foobar"));
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

TEST_F("require that regexp can be made from suffix string", ExprFixture()) {
    for (const auto& str: f1.expressions) {
        auto re = Regex::from_pattern(std::string(RegexpUtil::make_from_suffix(str)));
        ASSERT_TRUE(re.parsed_ok());

        EXPECT_TRUE(re.partial_match(str));
        EXPECT_FALSE(re.partial_match(str + "foo"));
        EXPECT_TRUE(re.partial_match("foo" + str));
        EXPECT_FALSE(re.partial_match("foo" + str + "bar"));
    }
}

TEST_F("require that regexp can be made from substring string", ExprFixture()) {
    for (const auto& str: f1.expressions) {
        auto re = Regex::from_pattern(std::string(RegexpUtil::make_from_substring(str)));
        ASSERT_TRUE(re.parsed_ok());

        EXPECT_TRUE(re.partial_match(str));
        EXPECT_TRUE(re.partial_match(str + "foo"));
        EXPECT_TRUE(re.partial_match("foo" + str));
        EXPECT_TRUE(re.partial_match("foo" + str + "bar"));
    }
}

TEST("full_match requires expression to match entire input string") {
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

TEST("partial_match requires expression to match substring of input string") {
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

TEST("partial_match can be explicitly anchored") {
    EXPECT_TRUE(Regex::partial_match("abcc", "^abc"));
    EXPECT_FALSE(Regex::partial_match("aabc", "^abc"));
    EXPECT_TRUE(Regex::partial_match("aabc", "abc$"));
    EXPECT_FALSE(Regex::partial_match("abcc", "abc$"));
    EXPECT_TRUE(Regex::partial_match("abc", "^abc$"));
    EXPECT_FALSE(Regex::partial_match("aabc", "^abc$"));
    EXPECT_FALSE(Regex::partial_match("abcc", "^abc$"));
}

TEST("Regex instance returns parsed_ok() == false upon parse failure") {
    auto re = Regex::from_pattern("[a-z"); // Unterminated set
    EXPECT_FALSE(re.parsed_ok());
}

TEST("Regex that has failed parsing immediately returns false for matches") {
    auto re = Regex::from_pattern("[a-z");
    EXPECT_FALSE(re.parsed_ok());
    EXPECT_FALSE(re.partial_match("a"));
    EXPECT_FALSE(re.full_match("b"));
}

TEST("can create case-insensitive regex matcher") {
    auto re = Regex::from_pattern("hello", Regex::Options::IgnoreCase);
    ASSERT_TRUE(re.parsed_ok());
    EXPECT_TRUE(re.partial_match("HelLo world"));
    EXPECT_TRUE(re.full_match("HELLO"));
}

TEST("regex is case sensitive by default") {
    auto re = Regex::from_pattern("hello");
    ASSERT_TRUE(re.valid());
    ASSERT_TRUE(re.parsed_ok());
    EXPECT_FALSE(re.partial_match("HelLo world"));
    EXPECT_FALSE(re.full_match("HELLO"));
}

TEST("Test that default constructed regex is invalid.") {
    Regex dummy;
    ASSERT_FALSE(dummy.valid());
}

TEST("Can extract min/max prefix range from anchored regex") {
    auto min_max = Regex::from_pattern("^.*").possible_anchored_match_prefix_range();
    EXPECT_EQUAL(min_max.first,  "");
    EXPECT_GREATER_EQUAL(min_max.second, "\xf4\x8f\xbf\xc0"); // Highest possible Unicode char (U+10FFFF) as UTF-8, plus 1

    min_max = Regex::from_pattern("^hello").possible_anchored_match_prefix_range();
    EXPECT_EQUAL(min_max.first,  "hello");
    EXPECT_EQUAL(min_max.second, "hello");

    min_max = Regex::from_pattern("^hello|^world").possible_anchored_match_prefix_range();
    EXPECT_EQUAL(min_max.first,  "hello");
    EXPECT_EQUAL(min_max.second, "world");

    min_max = Regex::from_pattern("(^hello|^world|^zoidberg)").possible_anchored_match_prefix_range();
    EXPECT_EQUAL(min_max.first,  "hello");
    EXPECT_EQUAL(min_max.second, "zoidberg");

    min_max = Regex::from_pattern("^hello (foo|bar|zoo)").possible_anchored_match_prefix_range();
    EXPECT_EQUAL(min_max.first,  "hello bar");
    EXPECT_EQUAL(min_max.second, "hello zoo");

    min_max = Regex::from_pattern("^(hello|world)+").possible_anchored_match_prefix_range();
    EXPECT_EQUAL(min_max.first,  "hello");
    EXPECT_EQUAL(min_max.second, "worldwp");

    // Bad regex; no range
    min_max = Regex::from_pattern("*hello").possible_anchored_match_prefix_range();
    EXPECT_EQUAL(min_max.first,  "");
    EXPECT_EQUAL(min_max.second, "");
}

TEST_MAIN() { TEST_RUN_ALL(); }
