// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/datatype/annotationtype.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/juniper/juniper_separators.h>
#include <vespa/searchsummary/docsummary/annotation_converter.h>
#include <vespa/searchsummary/docsummary/i_juniper_converter.h>
#include <vespa/searchsummary/docsummary/linguisticsannotation.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>

using document::Annotation;
using document::AnnotationType;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::Span;
using document::SpanList;
using document::SpanTree;
using document::StringFieldValue;
using search::docsummary::AnnotationConverter;
using search::docsummary::IJuniperConverter;
using search::linguistics::SPANTREE_NAME;
using vespalib::Slime;
using vespalib::slime::SlimeInserter;

namespace {

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

class MockJuniperConverter : public IJuniperConverter
{
    vespalib::string _result;
public:
    void convert(vespalib::stringref input, vespalib::slime::Inserter&) override {
        _result = input;
    }
    const vespalib::string& get_result() const noexcept { return _result; }
};

}

class AnnotationConverterTest : public testing::Test
{
protected:
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType*                     _document_type;
    document::FixedTypeRepo                 _fixed_repo;

    AnnotationConverterTest();
    ~AnnotationConverterTest() override;
    void set_span_tree(StringFieldValue& value, std::unique_ptr<SpanTree> tree);
    StringFieldValue make_annotated_string();
    StringFieldValue make_annotated_chinese_string();
    vespalib::string make_exp_il_annotated_string();
    vespalib::string make_exp_il_annotated_chinese_string();
    void expect_annotated(const vespalib::string& exp, const StringFieldValue& fv);
};

AnnotationConverterTest::AnnotationConverterTest()
    : testing::Test(),
      _repo(std::make_unique<DocumentTypeRepo>(get_document_types_config())),
      _document_type(_repo->getDocumentType("indexingdocument")),
      _fixed_repo(*_repo, *_document_type)
{
}

AnnotationConverterTest::~AnnotationConverterTest() = default;

void
AnnotationConverterTest::set_span_tree(StringFieldValue & value, std::unique_ptr<SpanTree> tree)
{
    StringFieldValue::SpanTrees trees;
    trees.push_back(std::move(tree));
    value.setSpanTrees(trees, _fixed_repo);
}

StringFieldValue
AnnotationConverterTest::make_annotated_string()
{
    auto span_list_up = std::make_unique<SpanList>();
    auto span_list = span_list_up.get();
    auto tree = std::make_unique<SpanTree>(SPANTREE_NAME, std::move(span_list_up));
    tree->annotate(span_list->add(std::make_unique<Span>(0, 3)), *AnnotationType::TERM);
    tree->annotate(span_list->add(std::make_unique<Span>(4, 3)),
                   Annotation(*AnnotationType::TERM, std::make_unique<StringFieldValue>("baz")));
    StringFieldValue value("foo bar");
    set_span_tree(value, std::move(tree));
    return value;
}

StringFieldValue
AnnotationConverterTest::make_annotated_chinese_string()
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
AnnotationConverterTest::make_exp_il_annotated_string()
{
    using namespace juniper::separators;
    vespalib::asciistream exp;
    exp << "foo" << unit_separator_string <<
        " " << unit_separator_string << interlinear_annotation_anchor_string <<
        "bar" << interlinear_annotation_separator_string <<
        "baz" << interlinear_annotation_terminator_string << unit_separator_string;
    return exp.str();
}

vespalib::string
AnnotationConverterTest::make_exp_il_annotated_chinese_string()
{
    using namespace juniper::separators;
    vespalib::asciistream exp;
    exp << "我就是那个" << unit_separator_string <<
        "大灰狼" << unit_separator_string;
    return exp.str();
}

void
AnnotationConverterTest::expect_annotated(const vespalib::string& exp, const StringFieldValue& fv)
{
    MockJuniperConverter juniper_converter;
    AnnotationConverter annotation_converter(juniper_converter);
    Slime slime;
    SlimeInserter inserter(slime);
    annotation_converter.convert(fv, inserter);
    EXPECT_EQ(exp, juniper_converter.get_result());
}


TEST_F(AnnotationConverterTest, convert_plain_string)
{
    using namespace juniper::separators;
    vespalib::string exp("Foo Bar Baz");
    StringFieldValue plain_string("Foo Bar Baz");
    expect_annotated(exp + unit_separator_string, plain_string);
}

TEST_F(AnnotationConverterTest, convert_annotated_string)
{
    auto exp = make_exp_il_annotated_string();
    auto annotated_string = make_annotated_string();
    expect_annotated(exp, annotated_string);
}

TEST_F(AnnotationConverterTest, convert_annotated_chinese_string)
{
        auto exp = make_exp_il_annotated_chinese_string();
        auto annotated_chinese_string = make_annotated_chinese_string();
        expect_annotated(exp, annotated_chinese_string);
}

GTEST_MAIN_RUN_ALL_TESTS()
