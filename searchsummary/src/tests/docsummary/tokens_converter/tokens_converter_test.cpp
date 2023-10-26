// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/datatype/annotationtype.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/searchlib/util/linguisticsannotation.h>
#include <vespa/searchlib/util/token_extractor.h>
#include <vespa/searchsummary/docsummary/tokens_converter.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::Annotation;
using document::AnnotationType;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::Span;
using document::SpanList;
using document::SpanTree;
using document::StringFieldValue;
using search::docsummary::TokensConverter;
using search::linguistics::SPANTREE_NAME;
using search::linguistics::TokenExtractor;
using vespalib::SimpleBuffer;
using vespalib::Slime;
using vespalib::slime::JsonFormat;
using vespalib::slime::SlimeInserter;

namespace {

vespalib::string
slime_to_string(const Slime& slime)
{
    SimpleBuffer buf;
    JsonFormat::encode(slime, buf, true);
    return buf.get().make_string();
}

DocumenttypesConfig
get_document_types_config()
{
    using namespace document::config_builder;
    DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "indexingdocument",
                     Struct("indexingdocument.header"),
                     Struct("indexingdocument.body"));
    return builder.config();
}

}

class TokensConverterTest : public testing::Test
{
protected:
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType*                     _document_type;
    document::FixedTypeRepo                 _fixed_repo;
    vespalib::string                        _dummy_field_name;
    TokenExtractor                          _token_extractor;

    TokensConverterTest();
    ~TokensConverterTest() override;
    void set_span_tree(StringFieldValue& value, std::unique_ptr<SpanTree> tree);
    StringFieldValue make_annotated_string(bool alt_tokens);
    StringFieldValue make_annotated_chinese_string();
    vespalib::string make_exp_annotated_chinese_string_tokens();
    vespalib::string convert(const StringFieldValue& fv);
};

TokensConverterTest::TokensConverterTest()
    : testing::Test(),
      _repo(std::make_unique<DocumentTypeRepo>(get_document_types_config())),
      _document_type(_repo->getDocumentType("indexingdocument")),
      _fixed_repo(*_repo, *_document_type),
      _dummy_field_name(),
      _token_extractor(_dummy_field_name, 100)
{
}

TokensConverterTest::~TokensConverterTest() = default;

void
TokensConverterTest::set_span_tree(StringFieldValue & value, std::unique_ptr<SpanTree> tree)
{
    StringFieldValue::SpanTrees trees;
    trees.push_back(std::move(tree));
    value.setSpanTrees(trees, _fixed_repo);
}

StringFieldValue
TokensConverterTest::make_annotated_string(bool alt_tokens)
{
    auto span_list_up = std::make_unique<SpanList>();
    auto span_list = span_list_up.get();
    auto tree = std::make_unique<SpanTree>(SPANTREE_NAME, std::move(span_list_up));
    tree->annotate(span_list->add(std::make_unique<Span>(0, 3)), *AnnotationType::TERM);
    if (alt_tokens) {
        tree->annotate(span_list->add(std::make_unique<Span>(4, 3)), *AnnotationType::TERM);
    }
    tree->annotate(span_list->add(std::make_unique<Span>(4, 3)),
                   Annotation(*AnnotationType::TERM, std::make_unique<StringFieldValue>("baz")));
    StringFieldValue value("foo bar");
    set_span_tree(value, std::move(tree));
    return value;
}

StringFieldValue
TokensConverterTest::make_annotated_chinese_string()
{
    auto span_list_up = std::make_unique<SpanList>();
    auto span_list = span_list_up.get();
    auto tree = std::make_unique<SpanTree>(SPANTREE_NAME, std::move(span_list_up));
    // These chinese characters each use 3 bytes in their UTF8 encoding.
    tree->annotate(span_list->add(std::make_unique<Span>(0, 15)), *AnnotationType::TERM);
    tree->annotate(span_list->add(std::make_unique<Span>(15, 9)), *AnnotationType::TERM);
    StringFieldValue value("我就是那个大灰狼");
    set_span_tree(value, std::move(tree));
    return value;
}

vespalib::string
TokensConverterTest::make_exp_annotated_chinese_string_tokens()
{
    return R"(["我就是那个","大灰狼"])";
}

vespalib::string
TokensConverterTest::convert(const StringFieldValue& fv)
{
    TokensConverter converter(_token_extractor);
    Slime slime;
    SlimeInserter inserter(slime);
    converter.convert(fv, inserter);
    return slime_to_string(slime);
}

TEST_F(TokensConverterTest, convert_empty_string)
{
    vespalib::string exp(R"([])");
    StringFieldValue plain_string("");
    EXPECT_EQ(exp, convert(plain_string));
}

TEST_F(TokensConverterTest, convert_plain_string)
{
    vespalib::string exp(R"(["Foo Bar Baz"])");
    StringFieldValue plain_string("Foo Bar Baz");
    EXPECT_EQ(exp, convert(plain_string));
}

TEST_F(TokensConverterTest, convert_annotated_string)
{
    vespalib::string exp(R"(["foo","baz"])");
    auto annotated_string = make_annotated_string(false);
    EXPECT_EQ(exp, convert(annotated_string));
}

TEST_F(TokensConverterTest, convert_annotated_string_with_alternatives)
{
    vespalib::string exp(R"(["foo",["bar","baz"]])");
    auto annotated_string = make_annotated_string(true);
    EXPECT_EQ(exp, convert(annotated_string));
}

TEST_F(TokensConverterTest, convert_annotated_chinese_string)
{
    auto exp = make_exp_annotated_chinese_string_tokens();
    auto annotated_chinese_string = make_annotated_chinese_string();
    EXPECT_EQ(exp, convert(annotated_chinese_string));
}

GTEST_MAIN_RUN_ALL_TESTS()
