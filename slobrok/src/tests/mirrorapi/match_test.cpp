// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/slobrok/sbmirror.h>

class MatchTester : public slobrok::api::IMirrorAPI
{
    SpecList lookup(vespalib::stringref ) const override {
        return SpecList();
    }
    uint32_t updates() const override { return 0; }
    bool ready() const override { return true; }

    const std::string name;

    void testMatch(const char *n, const char *p, bool expected)
    {
        TEST_STATE(n);
        TEST_STATE(p);
        EXPECT_EQUAL(expected, match(n, p));
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


TEST("require that pattern matches same string") {
    std::string pattern = "foo/bar*zot/qux?foo**bar*/*nop*";
    MatchTester name(pattern);
    name.mustMatch(pattern);
}

TEST("require that star is prefix match") {
    MatchTester name("foo/bar.foo/qux.bar/bar123/nop000");
    name.mustMatch("foo/bar.*/qux.*/bar*/nop*");
}

TEST("require that star matches empty string") {
    MatchTester name("foo/bar./qux./bar/nop");
    name.mustMatch("foo/bar.*/qux.*/bar*/nop*");
}

TEST("require that extra char before slash does not match") {
    MatchTester name("foo1/bar");
    name.mustNotMatch("foo/*");
}

TEST("require that star does not match multiple levels") {
    MatchTester name1("foo/bar/qux");
    MatchTester name2("foo/bar/bar/qux");
    name1.mustMatch("foo/*/qux");
    name2.mustNotMatch("foo/*/qux");
}

TEST("require that double star matches multiple levels") {
    MatchTester name("foo/bar.foo/qux.bar/bar123/nop000");
    name.mustMatch("**");
    name.mustMatch("f**");
    name.mustMatch("foo**");
    name.mustMatch("foo/**");
    name.mustMatch("foo*/**");
}

TEST("require that double star matches nothing") {
    MatchTester name("A");
    name.mustMatch("A**");
}

TEST("require that double star eats rest of name") {
    MatchTester name("foo/bar/baz/suffix");
    name.mustNotMatch("foo/**/suffix");
}

TEST_MAIN() { TEST_RUN_ALL(); }
