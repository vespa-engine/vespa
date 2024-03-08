// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchlib/expression/constantnode.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/interpolated_document_field_lookup_node.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::DataType;
using document::Document;
using document::DoubleFieldValue;
using search::expression::ConstantNode;
using search::expression::DocumentAccessorNode;
using search::expression::FloatResultNode;
using search::expression::InterpolatedDocumentFieldLookupNode;
using search::test::DocBuilder;

const vespalib::string field_name("f");

struct Fixture {
    DocBuilder                            _builder;
    std::unique_ptr<Document>             _doc;
    std::unique_ptr<DocumentAccessorNode> _node;

    Fixture();
    ~Fixture();

    Fixture& setup_doc(std::vector<double> field_value);
    Fixture& setup_node(double lookup_value);
    double evaluate();
};

Fixture::Fixture()
    :  _builder([](auto& header)
                {  header.addField(field_name, document::config_builder::Array(DataType::T_DOUBLE)); }),
       _doc(_builder.make_document("id:ns:searchdocument::0")),
       _node()
{
}

Fixture::~Fixture() = default;


Fixture&
Fixture::setup_doc(std::vector<double> field_value)
{
    auto array = _builder.make_array(field_name);
    for (auto& v : field_value) {
        array.add(DoubleFieldValue(v));
    }
    _doc->setValue(field_name, array);
    return *this;
}

Fixture&
Fixture::setup_node(double lookup_value)
{
    _node = std::make_unique<InterpolatedDocumentFieldLookupNode>(field_name, std::make_unique<ConstantNode>(std::make_unique<FloatResultNode>(lookup_value)));
    _node->prepare(true);
    _node->setDocType(_doc->getType());
    _node->setDoc(*_doc);
    return *this;
}

double
Fixture::evaluate()
{
    EXPECT_TRUE(_node->execute());
    return _node->getResult()->getFloat();
}


class InterpolatedDocumentFieldLookupNodeTest : public Fixture,
                                                public ::testing::Test
{
protected:
    InterpolatedDocumentFieldLookupNodeTest();
    ~InterpolatedDocumentFieldLookupNodeTest() override;
};

InterpolatedDocumentFieldLookupNodeTest::InterpolatedDocumentFieldLookupNodeTest() = default;
InterpolatedDocumentFieldLookupNodeTest::~InterpolatedDocumentFieldLookupNodeTest() = default;

TEST_F(InterpolatedDocumentFieldLookupNodeTest, test_interpolated_lookup_in_document_field)
{
    EXPECT_EQ(0.0, setup_doc({ 2, 10 }).setup_node(1.0).evaluate());
    EXPECT_EQ(0.0, setup_node(2.0).evaluate());
    EXPECT_EQ(0.3125, setup_node(4.5).evaluate());
    EXPECT_EQ(1.0, setup_node(10).evaluate());
    EXPECT_EQ(1.0, setup_node(11).evaluate());
    EXPECT_EQ(2.5, setup_doc({1.5, 5.25, 8.0, 14.0 }).evaluate());
}

GTEST_MAIN_RUN_ALL_TESTS()
