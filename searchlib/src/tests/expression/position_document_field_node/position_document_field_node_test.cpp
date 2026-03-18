// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchlib/expression/position_document_field_node.h>
#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::ArrayFieldValue;
using document::Document;
using document::IntFieldValue;
using document::PositionDataType;
using document::StructFieldValue;
using search::expression::DocumentAccessorNode;
using search::expression::IntegerResultNodeVector;
using search::expression::PositionDocumentFieldNode;
using search::expression::ResultNode;
using search::expression::ResultNodeVector;
using search::test::DocBuilder;
using vespalib::geo::ZCurve;

const std::string field_name("pos");

namespace {

StructFieldValue make_position(int32_t x, int32_t y) {
    auto fv = PositionDataType::getInstance().createFieldValue();
    auto& pos = dynamic_cast<StructFieldValue&>(*fv);
    pos.setValue(PositionDataType::FIELD_X, IntFieldValue::make(x));
    pos.setValue(PositionDataType::FIELD_Y, IntFieldValue::make(y));
    return pos;
}

}

struct SingleValueFixture {
    DocBuilder                            _builder;
    std::unique_ptr<Document>             _doc;
    std::unique_ptr<DocumentAccessorNode> _node;

    SingleValueFixture();
    ~SingleValueFixture();

    SingleValueFixture& setup_doc(int32_t x, int32_t y);
    SingleValueFixture& setup_node();
    [[nodiscard]] int64_t evaluate() const;
};

SingleValueFixture::SingleValueFixture()
    : _builder([](auto& builder, auto& doc) noexcept
               { doc.addField(field_name, builder.positionType()); }),
      _doc(_builder.make_document("id:ns:searchdocument::0")) {
}

SingleValueFixture::~SingleValueFixture() = default;

SingleValueFixture& SingleValueFixture::setup_doc(int32_t x, int32_t y) {
    _doc->setValue(field_name, make_position(x, y));
    return *this;
}

SingleValueFixture& SingleValueFixture::setup_node() {
    _node = std::make_unique<PositionDocumentFieldNode>(field_name);
    _node->prepare(true);
    _node->setDocType(_doc->getType());
    _node->setDoc(*_doc);
    return *this;
}

int64_t SingleValueFixture::evaluate() const {
    _node->execute();
    return _node->getResult()->getInteger();
}

class SingleValuePositionTest : public SingleValueFixture, public ::testing::Test {
protected:
    SingleValuePositionTest();
    ~SingleValuePositionTest() override;
};

SingleValuePositionTest::SingleValuePositionTest() = default;
SingleValuePositionTest::~SingleValuePositionTest() = default;

TEST_F(SingleValuePositionTest, encodes_position_as_zcurve) {
    int32_t x = 42;
    int32_t y = 21;
    EXPECT_EQ(ZCurve::encode(x, y), setup_doc(x, y).setup_node().evaluate());
}

TEST_F(SingleValuePositionTest, encodes_negative_coordinates) {
    int32_t x = -100;
    int32_t y = 200;
    EXPECT_EQ(ZCurve::encode(x, y), setup_doc(x, y).setup_node().evaluate());
}

TEST_F(SingleValuePositionTest, returns_zero_without_document) {
    _node = std::make_unique<PositionDocumentFieldNode>(field_name);
    _node->prepare(true);
    _node->execute();
    ASSERT_EQ(nullptr, _node->getResult());
}

struct MultiValueFixture {
    DocBuilder                            _builder;
    std::unique_ptr<Document>             _doc;
    std::unique_ptr<DocumentAccessorNode> _node;

    MultiValueFixture();
    ~MultiValueFixture();

    MultiValueFixture& setup_node();
};

MultiValueFixture::MultiValueFixture()
    : _builder([](auto& builder, auto& doc) noexcept {
          auto arr = doc.createArray(builder.positionType());
          auto arr_ref = doc.registerArray(std::move(arr));
          doc.addField(field_name, arr_ref);
      }),
      _doc(_builder.make_document("id:ns:searchdocument::0")) {
}

MultiValueFixture::~MultiValueFixture() = default;

MultiValueFixture& MultiValueFixture::setup_node() {
    _node = std::make_unique<PositionDocumentFieldNode>(field_name);
    _node->prepare(true);
    _node->setDocType(_doc->getType());
    _node->setDoc(*_doc);
    return *this;
}

class MultiValuePositionTest : public MultiValueFixture, public ::testing::Test {
protected:
    MultiValuePositionTest();
    ~MultiValuePositionTest() override;
};

MultiValuePositionTest::MultiValuePositionTest() = default;
MultiValuePositionTest::~MultiValuePositionTest() = default;

TEST_F(MultiValuePositionTest, returns_vector_result_for_array_field) {
    auto arr = _builder.make_array(field_name);
    arr.add(make_position(10, 20));
    arr.add(make_position(30, 40));
    arr.add(make_position(50, 60));
    _doc->setValue(field_name, arr);

    setup_node();
    _node->execute();

    const auto* result = _node->getResult();
    ASSERT_NE(result, nullptr);
    ASSERT_TRUE(result->inherits(ResultNodeVector::classId));
    const auto& vec = static_cast<const IntegerResultNodeVector&>(*result);
    ASSERT_EQ(vec.size(), 3u);
    EXPECT_EQ(vec.get(0).getInteger(), ZCurve::encode(10, 20));
    EXPECT_EQ(vec.get(1).getInteger(), ZCurve::encode(30, 40));
    EXPECT_EQ(vec.get(2).getInteger(), ZCurve::encode(50, 60));
}

TEST_F(MultiValuePositionTest, returns_empty_vector_for_empty_array) {
    auto arr = _builder.make_array(field_name);
    _doc->setValue(field_name, arr);

    setup_node();
    _node->execute();

    const auto* result = _node->getResult();
    ASSERT_NE(result, nullptr);
    ASSERT_TRUE(result->inherits(ResultNodeVector::classId));
    const auto& vec = static_cast<const IntegerResultNodeVector&>(*result);
    EXPECT_EQ(vec.size(), 0u);
}

TEST_F(MultiValuePositionTest, single_element_array) {
    auto arr = _builder.make_array(field_name);
    arr.add(make_position(-100, 200));
    _doc->setValue(field_name, arr);

    setup_node();
    _node->execute();

    const auto* result = _node->getResult();
    ASSERT_NE(result, nullptr);
    ASSERT_TRUE(result->inherits(ResultNodeVector::classId));
    const auto& vec = static_cast<const IntegerResultNodeVector&>(*result);
    ASSERT_EQ(vec.size(), 1u);
    EXPECT_EQ(vec.get(0).getInteger(), ZCurve::encode(-100, 200));
}

GTEST_MAIN_RUN_ALL_TESTS()
