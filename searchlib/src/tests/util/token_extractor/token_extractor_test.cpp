// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/util/token_extractor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <variant>

using document::DataType;
using document::Document;
using document::StringFieldValue;
using search::linguistics::TokenExtractor;
using search::test::DocBuilder;
using search::test::StringFieldBuilder;

using AlternativeWords = std::vector<vespalib::string>;
using AlternativeWordsOrWord = std::variant<AlternativeWords, vespalib::string>;
using Words = std::vector<AlternativeWordsOrWord>;

namespace {

vespalib::string corrupt_word = "corruptWord";

vespalib::string field_name("stringfield");

std::unique_ptr<Document>
make_corrupted_document(DocBuilder &b, size_t wordOffset)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::18");
    doc->setValue(field_name, sfb.tokenize("before ").word(corrupt_word).tokenize(" after").build());
    vespalib::nbostream stream;
    doc->serialize(stream);
    std::vector<char> raw;
    raw.resize(stream.size());
    stream.read(&raw[0], stream.size());
    assert(wordOffset < corrupt_word.size());
    for (size_t i = 0; i + corrupt_word.size() <= raw.size(); ++i) {
        if (memcmp(&raw[i], corrupt_word.c_str(), corrupt_word.size()) == 0) {
            raw[i + wordOffset] = '\0';
            break;
        }
    }
    vespalib::nbostream badstream;
    badstream.write(&raw[0], raw.size());
    return std::make_unique<Document>(b.get_repo(), badstream);
}

}

class TokenExtractorTest : public ::testing::Test {
protected:
    using SpanTerm = TokenExtractor::SpanTerm;
    DocBuilder                _doc_builder;
    std::unique_ptr<Document> _doc;
    TokenExtractor            _token_extractor;
    std::vector<SpanTerm>     _terms;

    static constexpr size_t max_word_len = 20;

    TokenExtractorTest();
    ~TokenExtractorTest() override;

    static DocBuilder::AddFieldsType
    make_add_fields()
    {
        return [](auto& header) { header.addField(field_name, DataType::T_STRING); };
    }

    Words process(const StringFieldValue& value);
};

TokenExtractorTest::TokenExtractorTest()
    : _doc_builder(make_add_fields()),
      _doc(_doc_builder.make_document("id:ns:searchdocument::0")),
      _token_extractor(field_name, max_word_len),
      _terms()
{
}

TokenExtractorTest::~TokenExtractorTest() = default;

Words
TokenExtractorTest::process(const StringFieldValue& value)
{
    Words result;
    _terms.clear();
    auto span_trees = value.getSpanTrees();
    vespalib::stringref text = value.getValueRef();
    _token_extractor.extract(_terms, span_trees, text, _doc.get());
    auto it  = _terms.begin();
    auto ite = _terms.end();
    auto itn = it;
    for (; it != ite; ) {
        for (; itn != ite && itn->span == it->span; ++itn);
        if ((itn - it) > 1) {
            auto& alternatives = std::get<0>(result.emplace_back());
            for (;it != itn; ++it) {
                alternatives.emplace_back(it->word);
            }
        } else {
            result.emplace_back(vespalib::string(it->word));
            ++it;
        }
    }

    return result;
}

TEST_F(TokenExtractorTest, empty_string)
{
    EXPECT_EQ((Words{}), process(StringFieldValue("")));
}

TEST_F(TokenExtractorTest, plain_string)
{
    EXPECT_EQ((Words{"Plain string"}), process(StringFieldValue("Plain string")));
}

TEST_F(TokenExtractorTest, normal_string)
{
    StringFieldBuilder sfb(_doc_builder);
    EXPECT_EQ((Words{"Hello", "world"}), process(sfb.tokenize("Hello world").build()));
}

TEST_F(TokenExtractorTest, normalized_tokens)
{
    StringFieldBuilder sfb(_doc_builder);
    auto value = sfb.token("Hello", false).alt_word("hello").tokenize(" world").build();
    EXPECT_EQ("Hello world", value.getValue());
    EXPECT_EQ((Words{"hello", "world"}), process(value));
}

TEST_F(TokenExtractorTest, alternative_tokens)
{
    StringFieldBuilder sfb(_doc_builder);
    auto value = sfb.word("Hello").alt_word("hello").tokenize(" world").build();
    EXPECT_EQ("Hello world", value.getValue());
    EXPECT_EQ((Words{AlternativeWords{"Hello", "hello"}, "world"}), process(value));
}

TEST_F(TokenExtractorTest, word_with_nul_byte_is_truncated)
{
    auto doc = make_corrupted_document(_doc_builder, 7);
    EXPECT_EQ((Words{"before", "corrupt", "after"}), process(dynamic_cast<const StringFieldValue&>(*doc->getValue(field_name))));
}

TEST_F(TokenExtractorTest, word_with_nul_byte_at_start_is_dropped)
{
    auto doc = make_corrupted_document(_doc_builder, 0);
    EXPECT_EQ((Words{"before", "after"}), process(dynamic_cast<const StringFieldValue&>(*doc->getValue(field_name))));
}

TEST_F(TokenExtractorTest, too_long_word_is_dropped)
{
    StringFieldBuilder sfb(_doc_builder);
    EXPECT_EQ((Words{"before", "after"}), process(sfb.tokenize("before veryverylongwordthatwillbedropped after").build()));
}

GTEST_MAIN_RUN_ALL_TESTS()
