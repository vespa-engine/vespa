// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/datatype/annotationtype.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cassert>
#include <iostream>

using document::Annotation;
using document::AnnotationType;
using document::Span;
using document::SpanNode;
using document::SpanTree;
using document::StringFieldValue;
using search::test::DocBuilder;
using search::test::StringFieldBuilder;

namespace
{

const vespalib::string SPANTREE_NAME("linguistics");

struct MyAnnotation {
    int32_t start;
    int32_t length;
    std::optional<vespalib::string> label;

    MyAnnotation(int32_t start_in, int32_t length_in) noexcept
        : start(start_in),
          length(length_in),
          label()
    {
    }

    MyAnnotation(int32_t start_in, int32_t length_in, vespalib::string label_in) noexcept
        : start(start_in),
          length(length_in),
          label(label_in)
    {
    }

    bool operator==(const MyAnnotation& rhs) const noexcept;
};

bool
MyAnnotation::operator==(const MyAnnotation& rhs) const noexcept
{
    return start == rhs.start &&
        length == rhs.length &&
        label == rhs.label;
}


std::ostream& operator<<(std::ostream& os, const MyAnnotation& ann) {
    os << "[" << ann.start << "," << ann.length << "]";
    if (ann.label.has_value()) {
        os << "(\"" << ann.label.value() << "\")";
    }
    return os;
}

}

class StringFieldBuilderTest : public testing::Test
{
protected:
    DocBuilder    db;
    StringFieldBuilder sfb;
    StringFieldBuilderTest();
    ~StringFieldBuilderTest();
    std::vector<MyAnnotation> get_annotations(const StringFieldValue& val);
    void assert_annotations(std::vector<MyAnnotation> exp, const vespalib::string& plain, const StringFieldValue& val);
};

StringFieldBuilderTest::StringFieldBuilderTest()
    : testing::Test(),
      db(),
      sfb(db)
{
}

StringFieldBuilderTest::~StringFieldBuilderTest() = default;

std::vector<MyAnnotation>
StringFieldBuilderTest::get_annotations(const StringFieldValue& val)
{
    std::vector<MyAnnotation> result;
    StringFieldValue::SpanTrees trees = val.getSpanTrees();
    const auto* tree = StringFieldValue::findTree(trees, SPANTREE_NAME);
    if (tree != nullptr) {
        for (auto& ann : *tree) {
            assert(ann.getType() == *AnnotationType::TERM);
            auto span = dynamic_cast<const Span *>(ann.getSpanNode());
            if (span == nullptr) {
                continue;
            }
            auto ann_fv = ann.getFieldValue();
            if (ann_fv == nullptr) {
                result.emplace_back(span->from(), span->length());
            } else {
                result.emplace_back(span->from(), span->length(), dynamic_cast<const StringFieldValue &>(*ann_fv).getValue());
            }
        }
    }
    return result;
}

void
StringFieldBuilderTest::assert_annotations(std::vector<MyAnnotation> exp, const vespalib::string& plain, const StringFieldValue& val)
{
    EXPECT_EQ(exp, get_annotations(val));
    EXPECT_EQ(plain, val.getValue());
}

TEST_F(StringFieldBuilderTest, no_annotations)
{
    assert_annotations({}, "foo", StringFieldValue("foo"));
}

TEST_F(StringFieldBuilderTest, single_word)
{
    assert_annotations({{0, 4}}, "word", sfb.word("word").build());
}

TEST_F(StringFieldBuilderTest, tokenize)
{
    assert_annotations({{0, 4}, {5, 2}, {8, 1}, {10, 4}}, "this is a test", sfb.tokenize("this is a test").build());
}

TEST_F(StringFieldBuilderTest, alt_word)
{
    assert_annotations({{0, 3}, {4, 3}, {4, 3, "baz"}}, "foo bar", sfb.word("foo").space().word("bar").alt_word("baz").build());
}

GTEST_MAIN_RUN_ALL_TESTS()
