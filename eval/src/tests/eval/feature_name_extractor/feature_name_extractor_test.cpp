// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/feature_name_extractor.h>

using vespalib::eval::FeatureNameExtractor;

void verify_extract(const vespalib::string &input,
                    const vespalib::string &expect_symbol,
                    const vespalib::string &expect_after)
{
    FeatureNameExtractor extractor;
    const char *pos_in = input.data();
    const char *end_in = input.data() + input.size();
    vespalib::string symbol_out;
    const char *pos_out = nullptr;
    extractor.extract_symbol(pos_in, end_in, pos_out, symbol_out);
    ASSERT_TRUE(pos_out != nullptr);
    vespalib::string after(pos_out, end_in);
    EXPECT_EQUAL(expect_symbol, symbol_out);
    EXPECT_EQUAL(expect_after, after);
}

TEST("require that basic names are extracted correctly") {
    TEST_DO(verify_extract("foo+", "foo", "+"));
    TEST_DO(verify_extract("foo.out+", "foo.out", "+"));
    TEST_DO(verify_extract("foo(p1,p2)+", "foo(p1,p2)", "+"));
    TEST_DO(verify_extract("foo(p1,p2).out+", "foo(p1,p2).out", "+"));
}

TEST("require that special characters are allowed in prefix and suffix") {
    TEST_DO(verify_extract("_@$+", "_@$", "+"));
    TEST_DO(verify_extract("_@$.$@_+", "_@$.$@_", "+"));
    TEST_DO(verify_extract("_@$(p1,p2)+", "_@$(p1,p2)", "+"));
    TEST_DO(verify_extract("_@$(p1,p2).$@_+", "_@$(p1,p2).$@_", "+"));
}

TEST("require that dot is only allowed in suffix") {
    TEST_DO(verify_extract("foo.bar+", "foo.bar", "+"));
    TEST_DO(verify_extract("foo.bar.out+", "foo.bar.out", "+"));
    TEST_DO(verify_extract("foo.bar(p1,p2)+", "foo.bar", "(p1,p2)+"));
    TEST_DO(verify_extract("foo.bar(p1,p2).out+", "foo.bar", "(p1,p2).out+"));
    TEST_DO(verify_extract("foo(p1,p2).out.bar+", "foo(p1,p2).out.bar", "+"));
}

TEST("require that parameters can be nested") {
    TEST_DO(verify_extract("foo(p1(a,b),p2(c,d(e,f))).out+", "foo(p1(a,b),p2(c,d(e,f))).out", "+"));
}

TEST("require that space is allowed among parameters") {
    TEST_DO(verify_extract("foo( p1 ( a , b ) ).out+", "foo( p1 ( a , b ) ).out", "+"));
}

TEST("require that space is now allowed outside parameters") {
    TEST_DO(verify_extract("foo +", "foo", " +"));
    TEST_DO(verify_extract("foo . out+", "foo", " . out+"));
    TEST_DO(verify_extract("foo. out+", "foo.", " out+"));
    TEST_DO(verify_extract("foo (p1,p2)+", "foo", " (p1,p2)+"));
    TEST_DO(verify_extract("foo(p1,p2) +", "foo(p1,p2)", " +"));
    TEST_DO(verify_extract("foo(p1,p2) .out+", "foo(p1,p2)", " .out+"));
    TEST_DO(verify_extract("foo(p1,p2).out +", "foo(p1,p2).out", " +"));
}

TEST("require that parameters can be scientific numbers") {
    TEST_DO(verify_extract("foo(1.3E+3,-1.9e-10).out+", "foo(1.3E+3,-1.9e-10).out", "+"));
}

TEST("require that quoted parenthesis are not counted") {
    TEST_DO(verify_extract("foo(a,b,\")\").out+", "foo(a,b,\")\").out", "+"));
}

TEST("require that escaped quotes does not unquote") {
    TEST_DO(verify_extract("foo(a,b,\"\\\")\").out+", "foo(a,b,\"\\\")\").out", "+"));
}

TEST("require that escaped escape does not hinder unquote") {
    TEST_DO(verify_extract("foo(a,b,\"\\\\\")\").out+", "foo(a,b,\"\\\\\")", "\").out+"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
