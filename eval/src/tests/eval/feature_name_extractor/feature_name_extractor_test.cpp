// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/feature_name_extractor.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::eval::FeatureNameExtractor;

void verify_extract(const std::string &input,
                    const std::string &expect_symbol,
                    const std::string &expect_after)
{
    SCOPED_TRACE(input);
    FeatureNameExtractor extractor;
    const char *pos_in = input.data();
    const char *end_in = input.data() + input.size();
    std::string symbol_out;
    const char *pos_out = nullptr;
    extractor.extract_symbol(pos_in, end_in, pos_out, symbol_out);
    ASSERT_TRUE(pos_out != nullptr);
    std::string after(pos_out, end_in);
    EXPECT_EQ(expect_symbol, symbol_out);
    EXPECT_EQ(expect_after, after);
}

TEST(FeatureNameExtractorTest, require_that_basic_names_are_extracted_correctly)
{
    verify_extract("foo+", "foo", "+");
    verify_extract("foo.out+", "foo.out", "+");
    verify_extract("foo(p1,p2)+", "foo(p1,p2)", "+");
    verify_extract("foo(p1,p2).out+", "foo(p1,p2).out", "+");
}

TEST(FeatureNameExtractorTest, require_that_special_characters_are_allowed_in_prefix_and_suffix)
{
    verify_extract("_@$+", "_@$", "+");
    verify_extract("_@$.$@_+", "_@$.$@_", "+");
    verify_extract("_@$(p1,p2)+", "_@$(p1,p2)", "+");
    verify_extract("_@$(p1,p2).$@_+", "_@$(p1,p2).$@_", "+");
}

TEST(FeatureNameExtractorTest, require_that_dot_is_only_allowed_in_suffix)
{
    verify_extract("foo.bar+", "foo.bar", "+");
    verify_extract("foo.bar.out+", "foo.bar.out", "+");
    verify_extract("foo.bar(p1,p2)+", "foo.bar", "(p1,p2)+");
    verify_extract("foo.bar(p1,p2).out+", "foo.bar", "(p1,p2).out+");
    verify_extract("foo(p1,p2).out.bar+", "foo(p1,p2).out.bar", "+");
}

TEST(FeatureNameExtractorTest, require_that_parameters_can_be_nested)
{
    verify_extract("foo(p1(a,b),p2(c,d(e,f))).out+", "foo(p1(a,b),p2(c,d(e,f))).out", "+");
}

TEST(FeatureNameExtractorTest, require_that_space_is_allowed_among_parameters)
{
    verify_extract("foo( p1 ( a , b ) ).out+", "foo( p1 ( a , b ) ).out", "+");
}

TEST(FeatureNameExtractorTest, require_that_space_is_now_allowed_outside_parameters)
{
    verify_extract("foo +", "foo", " +");
    verify_extract("foo . out+", "foo", " . out+");
    verify_extract("foo. out+", "foo.", " out+");
    verify_extract("foo (p1,p2)+", "foo", " (p1,p2)+");
    verify_extract("foo(p1,p2) +", "foo(p1,p2)", " +");
    verify_extract("foo(p1,p2) .out+", "foo(p1,p2)", " .out+");
    verify_extract("foo(p1,p2).out +", "foo(p1,p2).out", " +");
}

TEST(FeatureNameExtractorTest, require_that_parameters_can_be_scientific_numbers)
{
    verify_extract("foo(1.3E+3,-1.9e-10).out+", "foo(1.3E+3,-1.9e-10).out", "+");
}

TEST(FeatureNameExtractorTest, require_that_quoted_parenthesis_are_not_counted)
{
    verify_extract("foo(a,b,\")\").out+", "foo(a,b,\")\").out", "+");
}

TEST(FeatureNameExtractorTest, require_that_escaped_quotes_does_not_unquote)
{
    verify_extract("foo(a,b,\"\\\")\").out+", "foo(a,b,\"\\\")\").out", "+");
}

TEST(FeatureNameExtractorTest, require_that_escaped_escape_does_not_hinder_unquote)
{
    verify_extract("foo(a,b,\"\\\\\")\").out+", "foo(a,b,\"\\\\\")", "\").out+");
}

GTEST_MAIN_RUN_ALL_TESTS()
