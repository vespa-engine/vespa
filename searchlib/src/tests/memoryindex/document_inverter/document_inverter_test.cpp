// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/collectiondatatype.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/document_inverter_context.h>
#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/i_field_index_collection.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/memoryindex/mock_field_index_collection.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter_backend.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

namespace search::memoryindex {

using document::ArrayFieldValue;
using document::DataType;
using document::Document;
using document::MapFieldValue;
using document::StringFieldValue;
using document::StructDataType;
using document::StructFieldValue;
using index::FieldLengthCalculator;
using index::Schema;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;

namespace {

DocBuilder::AddFieldsType make_add_fields() {
    return [](auto& builder, auto& doc) noexcept {
        using namespace document::new_config_builder;
        auto string_array = doc.createArray(builder.stringTypeRef()).ref();
        auto string_wset = doc.createWset(builder.stringTypeRef()).ref();
        doc.addField("f0", builder.stringTypeRef())
            .addField("f1", builder.stringTypeRef())
            .addField("f2", string_array)
            .addField("f3", string_wset);
    };
}

Document::UP makeDoc10(DocBuilder& b) {
    StringFieldBuilder sfb(b);
    auto               doc = b.make_document("id:ns:searchdocument::10");
    doc->setValue("f0", sfb.tokenize("a b c d").build());
    return doc;
}

Document::UP makeDoc11(DocBuilder& b) {
    StringFieldBuilder sfb(b);
    auto               doc = b.make_document("id:ns:searchdocument::11");
    doc->setValue("f0", sfb.tokenize("a b e f").build());
    doc->setValue("f1", sfb.tokenize("a g").build());
    return doc;
}

Document::UP makeDoc12(DocBuilder& b) {
    StringFieldBuilder sfb(b);
    auto               doc = b.make_document("id:ns:searchdocument::12");
    doc->setValue("f0", sfb.tokenize("h doc12").build());
    return doc;
}

Document::UP makeDoc13(DocBuilder& b) {
    StringFieldBuilder sfb(b);
    auto               doc = b.make_document("id:ns:searchdocument::13");
    doc->setValue("f0", sfb.tokenize("i doc13").build());
    return doc;
}

Document::UP makeDoc14(DocBuilder& b) {
    StringFieldBuilder sfb(b);
    auto               doc = b.make_document("id:ns:searchdocument::14");
    doc->setValue("f0", sfb.tokenize("j doc14").build());
    return doc;
}

Document::UP makeDoc15(DocBuilder& b) {
    return b.make_document("id:ns:searchdocument::15");
}

Schema make_default_schema(DocBuilder& b) {
    return SchemaBuilder(b).add_all_indexes().build();
}

DocBuilder::AddFieldsType make_struct_add_fields() {
    return [](auto& builder, auto& doc) noexcept {
        using namespace document::new_config_builder;
        auto elem = doc.createStruct("elem")
                        .addField("name", builder.stringTypeRef())
                        .addField("other", builder.stringTypeRef())
                        .ref();
        auto elem_array = doc.createArray(elem).ref();
        auto string_map = doc.createMap(builder.stringTypeRef(), builder.stringTypeRef()).ref();
        auto elem_map = doc.createMap(builder.stringTypeRef(), elem).ref();
        doc.addField("sa", elem_array)
            .addField("m", string_map)
            .addField("sm", elem_map)
            .addField("s", elem)
            .addField("bad", elem_array);
    };
}

/*
 * Indexes of struct fields in an array of struct and maps, named by their field path and with one
 * array element per element in the complex field. Also indexes that cannot be struct field indexes:
 * a struct field in a plain struct, and a struct field index that is not an array of string.
 */
Schema make_struct_schema(DocBuilder&) {
    using search::index::schema::CollectionType;
    using search::index::schema::DataType;
    Schema schema;
    schema.addIndexField({"sa.name", DataType::STRING, CollectionType::ARRAY});
    schema.addIndexField({"m.key", DataType::STRING, CollectionType::ARRAY});
    schema.addIndexField({"m.value", DataType::STRING, CollectionType::ARRAY});
    schema.addIndexField({"sm.value.name", DataType::STRING, CollectionType::ARRAY});
    schema.addIndexField({"s.name", DataType::STRING, CollectionType::ARRAY});
    schema.addIndexField({"bad.name", DataType::STRING, CollectionType::SINGLE});
    return schema;
}

StructFieldValue make_elem(DocBuilder& b, const std::string& complex_field_name, const std::string& name) {
    StringFieldBuilder sfb(b);
    const auto&        field_type = b.get_document_type().getField(complex_field_name).getDataType();
    const auto*        array_type = field_type.cast_collection();
    const auto&        elem_type = dynamic_cast<const StructDataType&>(
        array_type != nullptr ? array_type->getNestedType() : field_type.cast_map()->getValueType());
    StructFieldValue elem(elem_type);
    if (!name.empty()) { // when empty, name is missing, but the element must still be counted
        elem.setValue("name", sfb.tokenize(name).build());
    }
    elem.setValue("other", sfb.tokenize("not indexed").build());
    return elem;
}

ArrayFieldValue make_elem_array(DocBuilder& b, const std::string& field_name, const std::vector<std::string>& names) {
    auto array = b.make_array(field_name);
    for (const auto& name : names) {
        array.add(make_elem(b, field_name, name));
    }
    return array;
}

Document::UP make_struct_doc(DocBuilder& b, const std::string& id) {
    StringFieldBuilder sfb(b);
    auto               doc = b.make_document(id);
    doc->setValue("sa", make_elem_array(b, "sa", {"foo bar", "", "baz"}));
    MapFieldValue map(b.get_document_type().getField("m").getDataType());
    map.put(sfb.tokenize("key").build(), sfb.tokenize("value").build());
    doc->setValue("m", map);
    MapFieldValue elem_map(b.get_document_type().getField("sm").getDataType());
    elem_map.put(sfb.tokenize("k0").build(), make_elem(b, "sm", "qux"));
    elem_map.put(sfb.tokenize("k1").build(), make_elem(b, "sm", ""));
    elem_map.put(sfb.tokenize("k2").build(), make_elem(b, "sm", "quux"));
    doc->setValue("sm", elem_map);
    doc->setValue("s", make_elem(b, "sa", "plain"));
    doc->setValue("bad", make_elem_array(b, "bad", {"unused"}));
    return doc;
}

} // namespace

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

struct DocumentInverterTest : public ::testing::Test {
    DocBuilder                              _b;
    Schema                                  _schema;
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;
    WordStore                               _word_store;
    FieldIndexRemover                       _remover;
    test::OrderedFieldIndexInserterBackend  _inserter_backend;
    FieldLengthCalculator                   _calculator;
    test::MockFieldIndexCollection          _fic;
    DocumentInverterContext                 _inv_context;
    DocumentInverter                        _inv;

    DocumentInverterTest() : DocumentInverterTest(make_add_fields(), make_default_schema) {}

    DocumentInverterTest(DocBuilder::AddFieldsType add_fields, Schema (*make_schema)(DocBuilder&))
        : _b(std::move(add_fields)),
          _schema(make_schema(_b)),
          _invertThreads(SequencedTaskExecutor::create(invert_executor, 1)),
          _pushThreads(SequencedTaskExecutor::create(push_executor, 1)),
          _word_store(),
          _remover(_word_store),
          _inserter_backend(),
          _calculator(),
          _fic(_remover, _inserter_backend, _calculator),
          _inv_context(_schema, *_invertThreads, *_pushThreads, _fic),
          _inv(_inv_context) {}

    void pushDocuments() {
        vespalib::Gate gate;
        _inv.pushDocuments(std::make_shared<vespalib::GateCallback>(gate));
        gate.await();
    }
};

TEST_F(DocumentInverterTest, require_that_fresh_insert_works) {
    auto doc10 = makeDoc10(_b);
    _inv.invertDocument(10, *doc10, {});
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=c,a=10,"
              "w=d,a=10",
              _inserter_backend.toStr());
}

TEST_F(DocumentInverterTest, require_that_multiple_docs_work) {
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

TEST_F(DocumentInverterTest, require_that_remove_works) {
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

TEST_F(DocumentInverterTest, require_that_reput_works) {
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

TEST_F(DocumentInverterTest, require_that_abort_pending_doc_works) {
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

TEST_F(DocumentInverterTest, require_that_mix_of_add_and_remove_works) {
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

TEST_F(DocumentInverterTest, require_that_empty_document_can_be_inverted) {
    auto doc15 = makeDoc15(_b);
    _inv.invertDocument(15, *doc15, {});
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

struct StructFieldDocumentInverterTest : public DocumentInverterTest {
    StructFieldDocumentInverterTest() : DocumentInverterTest(make_struct_add_fields(), make_struct_schema) {}
};

TEST_F(StructFieldDocumentInverterTest, require_that_struct_fields_in_array_of_struct_and_maps_are_inverted) {
    auto doc = make_struct_doc(_b, "id:ns:searchdocument::20");
    _inv.invertDocument(20, *doc, {});
    _inserter_backend.setVerbose();
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=bar,a=20(e=0,w=1,l=2[1]),"
              "w=baz,a=20(e=2,w=1,l=1[0]),"
              "w=foo,a=20(e=0,w=1,l=2[0]),"
              "f=1,"
              "w=key,a=20(e=0,w=1,l=1[0]),"
              "f=2,"
              "w=value,a=20(e=0,w=1,l=1[0]),"
              "f=3,"
              "w=quux,a=20(e=2,w=1,l=1[0]),"
              "w=qux,a=20(e=0,w=1,l=1[0])",
              _inserter_backend.toStr());
}

TEST_F(StructFieldDocumentInverterTest, require_that_missing_complex_fields_give_no_words) {
    auto doc = _b.make_document("id:ns:searchdocument::21");
    _inv.invertDocument(21, *doc, {});
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

TEST_F(StructFieldDocumentInverterTest, require_that_unannotated_struct_fields_give_no_words) {
    auto doc = _b.make_document("id:ns:searchdocument::22");
    auto array = _b.make_array("sa");
    auto elem0 = make_elem(_b, "sa", "");
    elem0.setValue("name", StringFieldValue("not annotated"));
    array.add(elem0);
    array.add(make_elem(_b, "sa", "annotated"));
    doc->setValue("sa", array);
    _inv.invertDocument(22, *doc, {});
    _inserter_backend.setVerbose();
    pushDocuments();
    EXPECT_EQ("f=0,w=annotated,a=22(e=1,w=1,l=1[0])", _inserter_backend.toStr());
}

TEST_F(StructFieldDocumentInverterTest, require_that_struct_fields_can_be_reput) {
    auto doc = make_struct_doc(_b, "id:ns:searchdocument::20");
    auto doc2 = _b.make_document("id:ns:searchdocument::20");
    doc2->setValue("sa", make_elem_array(_b, "sa", {"baz", "foo"}));
    _inv.invertDocument(20, *doc, {});
    _inv.invertDocument(20, *doc2, {});
    _inserter_backend.setVerbose();
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=baz,a=20(e=0,w=1,l=1[0]),"
              "w=foo,a=20(e=1,w=1,l=1[0])",
              _inserter_backend.toStr());
}

TEST_F(StructFieldDocumentInverterTest, require_that_struct_fields_can_be_removed) {
    auto doc = make_struct_doc(_b, "id:ns:searchdocument::20");
    _inv.invertDocument(20, *doc, {});
    _inv.removeDocument(20);
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

TEST_F(StructFieldDocumentInverterTest, require_that_document_type_change_is_handled) {
    DocBuilder other_b(make_struct_add_fields());
    auto       doc = make_struct_doc(_b, "id:ns:searchdocument::20");
    auto       other_doc = other_b.make_document("id:ns:searchdocument::21");
    auto       doc_again = _b.make_document("id:ns:searchdocument::22");
    other_doc->setValue("sa", make_elem_array(other_b, "sa", {"other"}));
    doc_again->setValue("sa", make_elem_array(_b, "sa", {"again"}));
    _inv.invertDocument(20, *doc, {});
    _inv.invertDocument(21, *other_doc, {});
    _inv.invertDocument(22, *doc_again, {});
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=again,a=22,"
              "w=bar,a=20,"
              "w=baz,a=20,"
              "w=foo,a=20,"
              "w=other,a=21,"
              "f=1,w=key,a=20,"
              "f=2,w=value,a=20,"
              "f=3,w=quux,a=20,w=qux,a=20",
              _inserter_backend.toStr());
}

} // namespace search::memoryindex

GTEST_MAIN_RUN_ALL_TESTS()
