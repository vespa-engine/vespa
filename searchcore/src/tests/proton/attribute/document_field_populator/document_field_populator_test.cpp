// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcore/proton/attribute/document_field_populator.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("document_field_populator_test");

using namespace document;
using namespace proton;
using namespace search;
using search::test::DocBuilder;

typedef search::attribute::Config AVConfig;
typedef search::attribute::BasicType AVBasicType;

struct DocContext
{
    DocBuilder _builder;
    DocContext()
        : _builder([](auto& header) { header.addField("a1", DataType::T_INT); })
    {
    }
    Document::UP create(uint32_t id) {
        vespalib::string docId =
                vespalib::make_string("id:searchdocument:searchdocument::%u", id);
        return _builder.make_document(docId);
    }
};

struct Fixture
{
    AttributeVector::SP _attr;
    IntegerAttribute &_intAttr;
    DocumentFieldPopulator _pop;
    DocContext _ctx;
    Fixture()
        : _attr(search::AttributeFactory::createAttribute("a1", AVConfig(AVBasicType::INT32))),
          _intAttr(dynamic_cast<IntegerAttribute &>(*_attr)),
          _pop("a1", _attr, "test"),
          _ctx()
    {
        _intAttr.addDocs(2);
        _intAttr.update(1, 100);
        _intAttr.commit();
    }
};

TEST_F("require that document field is populated based on attribute content", Fixture)
{
    // NOTE: DocumentFieldRetriever (used by DocumentFieldPopulator) is fully tested
    // with all data types in searchcore/src/tests/proton/server/documentretriever_test.cpp.
    {
        std::shared_ptr<Document> doc = f._ctx.create(1);
        f._pop.handleExisting(1, doc);
        EXPECT_EQUAL(100, doc->getValue("a1")->getAsInt());
    }
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
