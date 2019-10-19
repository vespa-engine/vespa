// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/util/exception.h>
#include <regex>

using namespace vespalib;

TEST("require that prefix detection works") {
    EXPECT_EQUAL("", Regexp::get_prefix(""));
    EXPECT_EQUAL("", Regexp::get_prefix("foo"));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo"));
    EXPECT_EQUAL("", Regexp::get_prefix("^foo|bar"));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo$"));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo[a-z]"));
    EXPECT_EQUAL("fo", Regexp::get_prefix("^foo{0,1}"));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo."));
    EXPECT_EQUAL("fo", Regexp::get_prefix("^foo*"));
    EXPECT_EQUAL("fo", Regexp::get_prefix("^foo?"));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo+"));
}

TEST("require that prefix detection sometimes underestimates the prefix size") {
    EXPECT_EQUAL("", Regexp::get_prefix("^^foo"));
    EXPECT_EQUAL("", Regexp::get_prefix("^foo(bar|baz)"));
    EXPECT_EQUAL("fo", Regexp::get_prefix("^foo{1,2}"));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo\\."));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo(bar)"));
    EXPECT_EQUAL("", Regexp::get_prefix("(^foo)"));
    EXPECT_EQUAL("", Regexp::get_prefix("^(foo)"));
    EXPECT_EQUAL("foo", Regexp::get_prefix("^foo[a]"));
    EXPECT_EQUAL("", Regexp::get_prefix("^foo|^foobar"));
}

const vespalib::string special("^|()[]{}.*?+\\$");

struct ExprFixture {
    std::vector<vespalib::string> expressions;
    ExprFixture() {
        expressions.push_back(special);
        for (char c: special) {
            expressions.push_back(vespalib::string(&c, 1));
        }
        expressions.push_back("abc");
        expressions.push_back("[:digit:]");
    }
};

TEST_F("require that regexp can be made from suffix string", ExprFixture()) {
    for (vespalib::string str: f1.expressions) {
        std::regex re(std::string(Regexp::make_from_suffix(str)));
        EXPECT_TRUE(std::regex_search(std::string(str), re));
        EXPECT_FALSE(std::regex_search(std::string(str + "foo"), re));
        EXPECT_TRUE(std::regex_search(std::string("foo" + str), re));
        EXPECT_FALSE(std::regex_search(std::string("foo" + str + "bar"), re));
    }
}

TEST_F("require that regexp can be made from substring string", ExprFixture()) {
    for (vespalib::string str: f1.expressions) {
        std::regex re(std::string(Regexp::make_from_substring(str)));
        EXPECT_TRUE(std::regex_search(std::string(str), re));
        EXPECT_TRUE(std::regex_search(std::string(str + "foo"), re));
        EXPECT_TRUE(std::regex_search(std::string("foo" + str), re));
        EXPECT_TRUE(std::regex_search(std::string("foo" + str + "bar"), re));
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
