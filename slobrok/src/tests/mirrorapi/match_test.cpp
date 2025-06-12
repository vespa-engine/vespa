// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/slobrok/sbmirror.h>

class MatchTester : public slobrok::api::IMirrorAPI
{
    SpecList lookup(std::string_view ) const override {
        return SpecList();
    }
    uint32_t updates() const override { return 0; }
    bool ready() const override { return true; }

    const std::string name;

    void testMatch(const char *n, const char *p, bool expected)
    {
        SCOPED_TRACE(n);
        SCOPED_TRACE(p);
        EXPECT_EQ(expected, match(n, p));
    }

public:
    MatchTester(const std::string &n) : name(n) {}

    void mustMatch(const std::string &pattern) {
        testMatch(name.c_str(), pattern.c_str(), true);
    }
    void mustNotMatch(const std::string &pattern) {
        testMatch(name.c_str(), pattern.c_str(), false);
    }
};


TEST(MatchTest, require_that_pattern_matches_same_string) {
    std::string pattern = "foo/bar*zot/qux?foo**bar*/*nop*";
    MatchTester name(pattern);
    name.mustMatch(pattern);
}

TEST(MatchTest, require_that_star_is_prefix_match) {
    MatchTester name("foo/bar.foo/qux.bar/bar123/nop000");
    name.mustMatch("foo/bar.*/qux.*/bar*/nop*");
}

TEST(MatchTest, require_that_star_matches_empty_string) {
    MatchTester name("foo/bar./qux./bar/nop");
    name.mustMatch("foo/bar.*/qux.*/bar*/nop*");
}

TEST(MatchTest, require_that_extra_char_before_slash_does_not_match) {
    MatchTester name("foo1/bar");
    name.mustNotMatch("foo/*");
}

TEST(MatchTest, require_that_star_does_not_match_multiple_levels) {
    MatchTester name1("foo/bar/qux");
    MatchTester name2("foo/bar/bar/qux");
    name1.mustMatch("foo/*/qux");
    name2.mustNotMatch("foo/*/qux");
}

TEST(MatchTest, require_that_double_star_matches_multiple_levels) {
    MatchTester name("foo/bar.foo/qux.bar/bar123/nop000");
    name.mustMatch("**");
    name.mustMatch("f**");
    name.mustMatch("foo**");
    name.mustMatch("foo/**");
    name.mustMatch("foo*/**");
}

TEST(MatchTest, require_that_double_star_matches_nothing) {
    MatchTester name("A");
    name.mustMatch("A**");
}

TEST(MatchTest, require_that_double_star_eats_rest_of_name) {
    MatchTester name("foo/bar/baz/suffix");
    name.mustNotMatch("foo/**/suffix");
}

GTEST_MAIN_RUN_ALL_TESTS()
