// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
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

using document::Document;
using index::DocBuilder;
using index::FieldLengthCalculator;
using index::Schema;
using index::schema::CollectionType;
using index::schema::DataType;
using vespalib::SequencedTaskExecutor;
using vespalib::ISequencedTaskExecutor;

namespace {

Document::UP
makeDoc10(DocBuilder &b)
{
    b.startDocument("id:ns:searchdocument::10");
    b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        endField();
    return b.endDocument();
}

Document::UP
makeDoc11(DocBuilder &b)
{
    b.startDocument("id:ns:searchdocument::11");
    b.startIndexField("f0").
        addStr("a").addStr("b").addStr("e").addStr("f").
        endField();
    b.startIndexField("f1").
        addStr("a").addStr("g").
        endField();
    return b.endDocument();
}

Document::UP
makeDoc12(DocBuilder &b)
{
    b.startDocument("id:ns:searchdocument::12");
    b.startIndexField("f0").
        addStr("h").addStr("doc12").
        endField();
    return b.endDocument();
}

Document::UP
makeDoc13(DocBuilder &b)
{
    b.startDocument("id:ns:searchdocument::13");
    b.startIndexField("f0").
        addStr("i").addStr("doc13").
        endField();
    return b.endDocument();
}

Document::UP
makeDoc14(DocBuilder &b)
{
    b.startDocument("id:ns:searchdocument::14");
    b.startIndexField("f0").
        addStr("j").addStr("doc14").
        endField();
    return b.endDocument();
}

Document::UP
makeDoc15(DocBuilder &b)
{
    b.startDocument("id:ns:searchdocument::15");
    return b.endDocument();
}

}

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

struct DocumentInverterTest : public ::testing::Test {
    Schema _schema;
    DocBuilder _b;
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;
    WordStore                       _word_store;
    FieldIndexRemover               _remover;
    test::OrderedFieldIndexInserterBackend _inserter_backend;
    FieldLengthCalculator           _calculator;
    test::MockFieldIndexCollection  _fic;
    DocumentInverterContext         _inv_context;
    DocumentInverter                _inv;

    static Schema makeSchema() {
        Schema schema;
        schema.addIndexField(Schema::IndexField("f0", DataType::STRING));
        schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
        schema.addIndexField(Schema::IndexField("f2", DataType::STRING, CollectionType::ARRAY));
        schema.addIndexField(Schema::IndexField("f3", DataType::STRING, CollectionType::WEIGHTEDSET));
        return schema;
    }

    DocumentInverterTest()
        : _schema(makeSchema()),
          _b(_schema),
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
    _inv.invertDocument(10, *makeDoc10(_b));
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=c,a=10,"
              "w=d,a=10",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_multiple_docs_work)
{
    _inv.invertDocument(10, *makeDoc10(_b));
    _inv.invertDocument(11, *makeDoc11(_b));
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
    _inv.invertDocument(10, *makeDoc10(_b));
    _inv.invertDocument(10, *makeDoc11(_b));
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

    _inv.invertDocument(10, *doc10);
    _inv.invertDocument(11, *doc11);
    _inv.removeDocument(10);
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=11,"
              "w=b,a=11,"
              "w=e,a=11,"
              "w=f,a=11,"
              "f=1,w=a,a=11,"
              "w=g,a=11",
              _inserter_backend.toStr());

    _inv.invertDocument(10, *doc10);
    _inv.invertDocument(11, *doc11);
    _inv.invertDocument(12, *doc12);
    _inv.invertDocument(13, *doc13);
    _inv.invertDocument(14, *doc14);
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

    _inv.invertDocument(10, *doc10);
    _inv.invertDocument(11, *doc11);
    _inv.invertDocument(12, *doc12);
    _inv.invertDocument(13, *doc13);
    _inv.invertDocument(14, *doc14);
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
    _inv.invertDocument(10, *makeDoc10(_b));
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
    _inv.invertDocument(15, *makeDoc15(_b));
    pushDocuments();
    EXPECT_EQ("",
              _inserter_backend.toStr());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
