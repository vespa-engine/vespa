// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/memoryindex/document_inverter_context.h>
#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/i_field_index_collection.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/searchlib/test/memoryindex/mock_field_index_collection.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter_backend.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

#include <vespa/vespalib/gtest/gtest.h>

namespace search::memoryindex {

using document::DataType;
using document::Document;
using index::FieldLengthCalculator;
using index::Schema;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
using vespalib::SequencedTaskExecutor;
using vespalib::ISequencedTaskExecutor;

namespace {

DocBuilder::AddFieldsType
make_add_fields()
{
    return [](auto& header) { using namespace document::config_builder;
        header.addField("f0", DataType::T_STRING)
            .addField("f1", DataType::T_STRING)
            .addField("f2", Array(DataType::T_STRING))
            .addField("f3", Wset(DataType::T_STRING));
            };
}

Document::UP
makeDoc10(DocBuilder &b)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::10");
    doc->setValue("f0", sfb.tokenize("a b c d").build());
    return doc;
}

Document::UP
makeDoc11(DocBuilder &b)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::11");
    doc->setValue("f0", sfb.tokenize("a b e f").build());
    doc->setValue("f1", sfb.tokenize("a g").build());
    return doc;
}

Document::UP
makeDoc12(DocBuilder &b)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::12");
    doc->setValue("f0", sfb.tokenize("h doc12").build());
    return doc;
}

Document::UP
makeDoc13(DocBuilder &b)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::13");
    doc->setValue("f0", sfb.tokenize("i doc13").build());
    return doc;
}

Document::UP
makeDoc14(DocBuilder &b)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::14");
    doc->setValue("f0", sfb.tokenize("j doc14").build());
    return doc;
}

Document::UP
makeDoc15(DocBuilder &b)
{
    return b.make_document("id:ns:searchdocument::15");
}

}

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

struct DocumentInverterTest : public ::testing::Test {
    DocBuilder _b;
    Schema _schema;
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;
    WordStore                       _word_store;
    FieldIndexRemover               _remover;
    test::OrderedFieldIndexInserterBackend _inserter_backend;
    FieldLengthCalculator           _calculator;
    test::MockFieldIndexCollection  _fic;
    DocumentInverterContext         _inv_context;
    DocumentInverter                _inv;

    DocumentInverterTest()
        : _b(make_add_fields()),
          _schema(SchemaBuilder(_b).add_all_indexes().build()),
          _invertThreads(SequencedTaskExecutor::create(invert_executor, 1)),
          _pushThreads(SequencedTaskExecutor::create(push_executor, 1)),
          _word_store(),
          _remover(_word_store),
          _inserter_backend(),
          _calculator(),
          _fic(_remover, _inserter_backend, _calculator),
          _inv_context(_schema, *_invertThreads, *_pushThreads, _fic),
          _inv(_inv_context)
    {
    }

    void pushDocuments() {
        vespalib::Gate gate;
        _inv.pushDocuments(std::make_shared<vespalib::GateCallback>(gate));
        gate.await();
    }
};

TEST_F(DocumentInverterTest, require_that_fresh_insert_works)
{
    auto doc10 = makeDoc10(_b);
    _inv.invertDocument(10, *doc10, {});
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=c,a=10,"
              "w=d,a=10",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_multiple_docs_work)
{
    auto doc10 = makeDoc10(_b);
    auto doc11 = makeDoc11(_b);
    _inv.invertDocument(10, *doc10, {});
    _inv.invertDocument(11, *doc11, {});
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,a=11,"
              "w=b,a=10,a=11,"
              "w=c,a=10,w=d,a=10,"
              "w=e,a=11,"
              "w=f,a=11,"
              "f=1,w=a,a=11,"
              "w=g,a=11",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_remove_works)
{
    _inv.getInverter(0)->remove("b", 10);
    _inv.getInverter(0)->remove("a", 10);
    _inv.getInverter(0)->remove("b", 11);
    _inv.getInverter(2)->remove("c", 12);
    _inv.getInverter(1)->remove("a", 10);
    pushDocuments();
    EXPECT_EQ("f=0,w=a,r=10,"
              "w=b,r=10,r=11,"
              "f=1,w=a,r=10,"
              "f=2,w=c,r=12",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_reput_works)
{
    auto doc10 = makeDoc10(_b);
    auto doc11 = makeDoc11(_b);
    _inv.invertDocument(10, *doc10, {});
    _inv.invertDocument(10, *doc11, {});
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=e,a=10,"
              "w=f,a=10,"
              "f=1,w=a,a=10,"
              "w=g,a=10",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_abort_pending_doc_works)
{
    auto doc10 = makeDoc10(_b);
    auto doc11 = makeDoc11(_b);
    auto doc12 = makeDoc12(_b);
    auto doc13 = makeDoc13(_b);
    auto doc14 = makeDoc14(_b);

    _inv.invertDocument(10, *doc10, {});
    _inv.invertDocument(11, *doc11, {});
    _inv.removeDocument(10);
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=11,"
              "w=b,a=11,"
              "w=e,a=11,"
              "w=f,a=11,"
              "f=1,w=a,a=11,"
              "w=g,a=11",
              _inserter_backend.toStr());

    _inv.invertDocument(10, *doc10, {});
    _inv.invertDocument(11, *doc11, {});
    _inv.invertDocument(12, *doc12, {});
    _inv.invertDocument(13, *doc13, {});
    _inv.invertDocument(14, *doc14, {});
    _inv.removeDocument(11);
    _inv.removeDocument(13);
    _inserter_backend.reset();
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=c,a=10,"
              "w=d,a=10,"
              "w=doc12,a=12,"
              "w=doc14,a=14,"
              "w=h,a=12,"
              "w=j,a=14",
              _inserter_backend.toStr());

    _inv.invertDocument(10, *doc10, {});
    _inv.invertDocument(11, *doc11, {});
    _inv.invertDocument(12, *doc12, {});
    _inv.invertDocument(13, *doc13, {});
    _inv.invertDocument(14, *doc14, {});
    _inv.removeDocument(11);
    _inv.removeDocument(12);
    _inv.removeDocument(13);
    _inv.removeDocument(14);
    _inserter_backend.reset();
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=c,a=10,"
              "w=d,a=10",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_mix_of_add_and_remove_works)
{
    _inv.getInverter(0)->remove("a", 11);
    _inv.getInverter(0)->remove("c", 9);
    _inv.getInverter(0)->remove("d", 10);
    _inv.getInverter(0)->remove("z", 12);
    auto doc10 = makeDoc10(_b);
    _inv.invertDocument(10, *doc10, {});
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,r=11,"
              "w=b,a=10,"
              "w=c,r=9,a=10,"
              "w=d,r=10,a=10,"
              "w=z,r=12",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_empty_document_can_be_inverted)
{
    auto doc15 = makeDoc15(_b);
    _inv.invertDocument(15, *doc15, {});
    pushDocuments();
    EXPECT_EQ("",
              _inserter_backend.toStr());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
