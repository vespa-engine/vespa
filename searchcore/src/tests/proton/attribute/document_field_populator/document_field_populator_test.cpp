// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcore/proton/attribute/document_field_populator.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("document_field_populator_test");

using namespace document;
using namespace proton;
using namespace search;
using search::test::DocBuilder;

using AVConfig = search::attribute::Config;
using AVBasicType = search::attribute::BasicType;

struct DocContext
{
    DocBuilder _builder;
    DocContext()
        : _builder([](auto& header) { header.addField("a1", DataType::T_INT); })
    {
    }
    Document::UP create(uint32_t id) {
        std::string docId =
                vespalib::make_string("id:searchdocument:searchdocument::%u", id);
        return _builder.make_document(docId);
    }
};

class DocumentFieldPopulatorTest : public ::testing::Test
{
protected:
    AttributeVector::SP _attr;
    IntegerAttribute &_intAttr;
    DocumentFieldPopulator _pop;
    DocContext _ctx;

    DocumentFieldPopulatorTest();
    ~DocumentFieldPopulatorTest() override;
};

DocumentFieldPopulatorTest::DocumentFieldPopulatorTest()
    : _attr(search::AttributeFactory::createAttribute("a1", AVConfig(AVBasicType::INT32))),
      _intAttr(dynamic_cast<IntegerAttribute &>(*_attr)),
      _pop("a1", _attr, "test"),
      _ctx()
{
    _intAttr.addDocs(2);
    _intAttr.update(1, 100);
    _intAttr.commit();
}

DocumentFieldPopulatorTest::~DocumentFieldPopulatorTest() = default;

TEST_F(DocumentFieldPopulatorTest, require_that_document_field_is_populated_based_on_attribute_content)
{
    // NOTE: DocumentFieldRetriever (used by DocumentFieldPopulator) is fully tested
    // with all data types in searchcore/src/tests/proton/server/documentretriever_test.cpp.
    {
        std::shared_ptr<Document> doc = _ctx.create(1);
        _pop.handleExisting(1, doc);
        EXPECT_EQ(100, doc->getValue("a1")->getAsInt());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
