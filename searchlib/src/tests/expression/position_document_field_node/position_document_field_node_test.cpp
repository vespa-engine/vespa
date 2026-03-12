// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchlib/expression/position_document_field_node.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::Document;
using document::IntFieldValue;
using document::PositionDataType;
using document::StructFieldValue;
using search::expression::DocumentAccessorNode;
using search::expression::PositionDocumentFieldNode;
using search::test::DocBuilder;
using vespalib::geo::ZCurve;

const std::string field_name("pos");

struct Fixture {
    DocBuilder                            _builder;
    std::unique_ptr<Document>             _doc;
    std::unique_ptr<DocumentAccessorNode> _node;

    Fixture();
    ~Fixture();

    Fixture& setup_doc(int32_t x, int32_t y);
    Fixture& setup_node();
    int64_t evaluate() const;
};

Fixture::Fixture()
    : _builder([](auto& builder, auto& doc) noexcept
               { doc.addField(field_name, builder.positionType()); }),
      _doc(_builder.make_document("id:ns:searchdocument::0")) {
}

Fixture::~Fixture() = default;

Fixture& Fixture::setup_doc(int32_t x, int32_t y) {
    auto fv = PositionDataType::getInstance().createFieldValue();
    auto& pos = dynamic_cast<StructFieldValue&>(*fv);
    pos.setValue(PositionDataType::FIELD_X, IntFieldValue::make(x));
    pos.setValue(PositionDataType::FIELD_Y, IntFieldValue::make(y));
    _doc->setValue(field_name, *fv);
    return *this;
}

Fixture& Fixture::setup_node() {
    _node = std::make_unique<PositionDocumentFieldNode>(field_name);
    _node->prepare(true);
    _node->setDocType(_doc->getType());
    _node->setDoc(*_doc);
    return *this;
}

int64_t Fixture::evaluate() const {
    _node->execute();
    return _node->getResult()->getInteger();
}

class PositionDocumentFieldNodeTest : public Fixture, public ::testing::Test {
protected:
    PositionDocumentFieldNodeTest();
    ~PositionDocumentFieldNodeTest() override;
};

PositionDocumentFieldNodeTest::PositionDocumentFieldNodeTest() = default;

PositionDocumentFieldNodeTest::~PositionDocumentFieldNodeTest() = default;

TEST_F(PositionDocumentFieldNodeTest, encodes_position_as_zcurve) {
    int32_t x = 42;
    int32_t y = 21;
    EXPECT_EQ(ZCurve::encode(x, y), setup_doc(x, y).setup_node().evaluate());
}

TEST_F(PositionDocumentFieldNodeTest, encodes_negative_coordinates) {
    int32_t x = -100;
    int32_t y = 200;
    EXPECT_EQ(ZCurve::encode(x, y), setup_doc(x, y).setup_node().evaluate());
}

TEST_F(PositionDocumentFieldNodeTest, returns_zero_without_document) {
    _node = std::make_unique<PositionDocumentFieldNode>(field_name);
    _node->prepare(true);
    _node->execute();
    EXPECT_EQ(0, _node->getResult()->getInteger());
}

GTEST_MAIN_RUN_ALL_TESTS()
