// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vsm/vsm/tokens_converter.h>

using document::StringFieldValue;
using search::Normalizing;
using vespalib::SimpleBuffer;
using vespalib::Slime;
using vespalib::slime::JsonFormat;
using vespalib::slime::SlimeInserter;
using vsm::TokensConverter;

namespace {

vespalib::string
slime_to_string(const Slime& slime)
{
    SimpleBuffer buf;
    JsonFormat::encode(slime, buf, true);
    return buf.get().make_string();
}

}

class TokensConverterTest : public testing::Test
{
protected:
    TokensConverterTest();
    ~TokensConverterTest() override;
;
    vespalib::string convert(const StringFieldValue& fv, bool exact_match, Normalizing normalize_mode);
};

TokensConverterTest::TokensConverterTest()
    : testing::Test()
{
}

TokensConverterTest::~TokensConverterTest() = default;

vespalib::string
TokensConverterTest::convert(const StringFieldValue& fv, bool exact_match, Normalizing normalize_mode)
{
    TokensConverter converter(exact_match, normalize_mode);
    Slime slime;
    SlimeInserter inserter(slime);
    converter.convert(fv, inserter);
    return slime_to_string(slime);
}

TEST_F(TokensConverterTest, convert_empty_string)
{
    vespalib::string exp(R"([])");
    StringFieldValue plain_string("");
    EXPECT_EQ(exp, convert(plain_string, false, Normalizing::NONE));
    EXPECT_EQ(exp, convert(plain_string, true, Normalizing::NONE));
}

TEST_F(TokensConverterTest, convert_exact_match)
{
    vespalib::string exp_none(R"([".Foo Bar Baz."])");
    vespalib::string exp_lowercase(R"([".foo bar baz."])");
    StringFieldValue plain_string(".Foo Bar Baz.");
    EXPECT_EQ(exp_none, convert(plain_string, true, Normalizing::NONE));
    EXPECT_EQ(exp_lowercase, convert(plain_string, true, Normalizing::LOWERCASE));
}

TEST_F(TokensConverterTest, convert_tokenized_string)
{
    vespalib::string exp_none(R"(["Foo","Bar"])");
    vespalib::string exp_lowercase(R"(["foo","bar"])");
    StringFieldValue value(".Foo Bar.");
    EXPECT_EQ(exp_none, convert(value, false, Normalizing::NONE));
    EXPECT_EQ(exp_lowercase, convert(value, false, Normalizing::LOWERCASE));
}

TEST_F(TokensConverterTest, convert_with_folding)
{
    vespalib::string exp_exact_match_folded(R"(["moerk vaarkveld"])");
    vespalib::string exp_tokenized_folded(R"(["moerk","vaarkveld"])");
    StringFieldValue value("Mørk vårkveld");
    EXPECT_EQ(exp_exact_match_folded, convert(value, true, Normalizing::LOWERCASE_AND_FOLD));
    EXPECT_EQ(exp_tokenized_folded, convert(value, false, Normalizing::LOWERCASE_AND_FOLD));
}

GTEST_MAIN_RUN_ALL_TESTS()
