// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter_backend.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace search {

using document::ArrayFieldValue;
using document::DataType;
using document::Document;
using document::WeightedSetFieldValue;
using index::Schema;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;

using namespace index;

namespace memoryindex {

namespace {

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

Document::UP
makeDoc16(DocBuilder &b)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::16");
    doc->setValue("f0", sfb.tokenize("foo bar baz").alt_word("altbaz").tokenize(" y").alt_word("alty").tokenize(" z").build());
    return doc;
}

Document::UP
makeDoc17(DocBuilder &b)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::17");
    doc->setValue("f1", sfb.tokenize("foo0 bar0").build());
    auto string_array = b.make_array("f2");
    string_array.add(sfb.tokenize("foo bar").build());
    string_array.add(sfb.tokenize("bar").build());
    doc->setValue("f2", string_array);
    auto string_wset = b.make_wset("f3");
    string_wset.add(sfb.tokenize("foo2 bar2").build(), 3);
    string_wset.add(sfb.tokenize("bar2").build(), 4);
    doc->setValue("f3", string_wset);
    return doc;
}

vespalib::string corruptWord = "corruptWord";

Document::UP
makeCorruptDocument(DocBuilder &b, size_t wordOffset)
{
    StringFieldBuilder sfb(b);
    auto doc = b.make_document("id:ns:searchdocument::18");
    doc->setValue("f0", sfb.tokenize("before ").word(corruptWord).tokenize(" after z").build());
    vespalib::nbostream stream;
    doc->serialize(stream);
    std::vector<char> raw;
    raw.resize(stream.size());
    stream.read(&raw[0], stream.size());
    assert(wordOffset < corruptWord.size());
    for (size_t i = 0; i + corruptWord.size() <= raw.size(); ++i) {
        if (memcmp(&raw[i], corruptWord.c_str(), corruptWord.size()) == 0) {
            raw[i + wordOffset] = '\0';
            break;
        }
    }
    vespalib::nbostream badstream;
    badstream.write(&raw[0], raw.size());
    return std::make_unique<Document>(b.get_repo(), badstream);
}

}

struct FieldInverterTest : public ::testing::Test {
    DocBuilder _b;
    Schema _schema;
    WordStore                       _word_store;
    FieldIndexRemover               _remover;
    test::OrderedFieldIndexInserterBackend _inserter_backend;
    std::vector<std::unique_ptr<FieldLengthCalculator>> _calculators;
    std::vector<std::unique_ptr<IOrderedFieldIndexInserter>> _inserters;
    std::vector<std::unique_ptr<FieldInverter> > _inverters;

    static DocBuilder::AddFieldsType
    make_add_fields()
    {
        return [](auto& header) { using namespace document::config_builder;
            header.addField("f0", DataType::T_STRING)
                .addField("f1", DataType::T_STRING)
                .addField("f2", Array(DataType::T_STRING))
                .addField("f3", Wset(DataType::T_STRING));
               };
    }

    FieldInverterTest();
    ~FieldInverterTest() override;

    void invertDocument(uint32_t docId, const Document &doc) {
        uint32_t fieldId = 0;
        for (auto &inverter : _inverters) {
            vespalib::stringref fieldName =
                _schema.getIndexField(fieldId).getName();
            inverter->invertField(docId, doc.getValue(fieldName));
            ++fieldId;
        }
    }

    void pushDocuments() {
        for (auto &inverter : _inverters) {
            inverter->pushDocuments();
        }
    }

    void removeDocument(uint32_t docId) {
        for (auto &inverter : _inverters) {
            inverter->removeDocument(docId);
        }
    }

    void assert_calculator(uint32_t field_id, double exp_avg, uint32_t exp_samples) {
        const auto &calc = *_calculators[field_id];
        EXPECT_DOUBLE_EQ(exp_avg, calc.get_average_field_length());
        EXPECT_EQ(exp_samples, calc.get_num_samples());
    }

};

FieldInverterTest::FieldInverterTest()
    : _b(make_add_fields()),
      _schema(SchemaBuilder(_b).add_all_indexes().build()),
      _word_store(),
      _remover(_word_store),
      _inserter_backend(),
      _calculators(),
      _inserters(),
      _inverters()
{
    for (uint32_t fieldId = 0; fieldId < _schema.getNumIndexFields(); ++fieldId) {
        _calculators.emplace_back(std::make_unique<FieldLengthCalculator>());
        _inserters.emplace_back(std::make_unique<test::OrderedFieldIndexInserter>(_inserter_backend, fieldId));
        _inverters.push_back(std::make_unique<FieldInverter>(_schema, fieldId, _remover, *_inserters.back(), *_calculators.back()));
    }
}
FieldInverterTest::~FieldInverterTest() = default;

TEST_F(FieldInverterTest, require_that_fresh_insert_works)
{
    invertDocument(10, *makeDoc10(_b));
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=c,a=10,"
              "w=d,a=10",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_multiple_docs_work)
{
    invertDocument(10, *makeDoc10(_b));
    invertDocument(11, *makeDoc11(_b));
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

TEST_F(FieldInverterTest, require_that_remove_works)
{
    _inverters[0]->remove("b", 10);
    _inverters[0]->remove("a", 10);
    _inverters[0]->remove("b", 11);
    _inverters[2]->remove("c", 12);
    _inverters[1]->remove("a", 10);
    pushDocuments();
    EXPECT_EQ("f=0,w=a,r=10,"
              "w=b,r=10,r=11,"
              "f=1,w=a,r=10,"
              "f=2,w=c,r=12",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_reput_works)
{
    invertDocument(10, *makeDoc10(_b));
    invertDocument(10, *makeDoc11(_b));
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=e,a=10,"
              "w=f,a=10,"
              "f=1,w=a,a=10,"
              "w=g,a=10",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_abort_pending_doc_works)
{
    auto doc10 = makeDoc10(_b);
    auto doc11 = makeDoc11(_b);
    auto doc12 = makeDoc12(_b);
    auto doc13 = makeDoc13(_b);
    auto doc14 = makeDoc14(_b);

    invertDocument(10, *doc10);
    invertDocument(11, *doc11);
    removeDocument(10);
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=11,"
              "w=b,a=11,"
              "w=e,a=11,"
              "w=f,a=11,"
              "f=1,w=a,a=11,"
              "w=g,a=11",
              _inserter_backend.toStr());

    invertDocument(10, *doc10);
    invertDocument(11, *doc11);
    invertDocument(12, *doc12);
    invertDocument(13, *doc13);
    invertDocument(14, *doc14);
    removeDocument(11);
    removeDocument(13);
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

    invertDocument(10, *doc10);
    invertDocument(11, *doc11);
    invertDocument(12, *doc12);
    invertDocument(13, *doc13);
    invertDocument(14, *doc14);
    removeDocument(11);
    removeDocument(12);
    removeDocument(13);
    removeDocument(14);
    _inserter_backend.reset();
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,"
              "w=b,a=10,"
              "w=c,a=10,"
              "w=d,a=10",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_mix_of_add_and_remove_works)
{
    _inverters[0]->remove("a", 11);
    _inverters[0]->remove("c", 9);
    _inverters[0]->remove("d", 10);
    _inverters[0]->remove("z", 12);
    invertDocument(10, *makeDoc10(_b));
    pushDocuments();
    EXPECT_EQ("f=0,w=a,a=10,r=11,"
              "w=b,a=10,"
              "w=c,r=9,a=10,"
              "w=d,r=10,a=10,"
              "w=z,r=12",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_empty_document_can_be_inverted)
{
    invertDocument(15, *makeDoc15(_b));
    pushDocuments();
    EXPECT_EQ("",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_multiple_words_at_same_position_works)
{
    invertDocument(16, *makeDoc16(_b));
    _inserter_backend.setVerbose();
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=altbaz,a=16(e=0,w=1,l=5[2]),"
              "w=alty,a=16(e=0,w=1,l=5[3]),"
              "w=bar,a=16(e=0,w=1,l=5[1]),"
              "w=baz,a=16(e=0,w=1,l=5[2]),"
              "w=foo,a=16(e=0,w=1,l=5[0]),"
              "w=y,a=16(e=0,w=1,l=5[3]),"
              "w=z,a=16(e=0,w=1,l=5[4])",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_interleaved_features_are_calculated)
{
    invertDocument(17, *makeDoc17(_b));
    _inserter_backend.setVerbose();
    _inserter_backend.set_show_interleaved_features();
    pushDocuments();
    EXPECT_EQ("f=1,"
              "w=bar0,a=17(fl=2,occs=1,e=0,w=1,l=2[1]),"
              "w=foo0,a=17(fl=2,occs=1,e=0,w=1,l=2[0]),"
              "f=2,"
              "w=bar,a=17(fl=3,occs=2,e=0,w=1,l=2[1],e=1,w=1,l=1[0]),"
              "w=foo,a=17(fl=3,occs=1,e=0,w=1,l=2[0]),"
              "f=3,"
              "w=bar2,a=17(fl=3,occs=2,e=0,w=3,l=2[1],e=1,w=4,l=1[0]),"
              "w=foo2,a=17(fl=3,occs=1,e=0,w=3,l=2[0])",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_average_field_length_is_calculated)
{
    invertDocument(10, *makeDoc10(_b));
    pushDocuments();
    assert_calculator(0, 4.0, 1);
    assert_calculator(1, 0.0, 0);
    invertDocument(11, *makeDoc11(_b));
    pushDocuments();
    assert_calculator(0, (4.0 + 4.0)/2, 2);
    assert_calculator(1, 2.0, 1);
    invertDocument(12, *makeDoc12(_b));
    pushDocuments();
    assert_calculator(0, (4.0 + 4.0 + 2.0)/3, 3);
    assert_calculator(1, 2.0, 1);
}

TEST_F(FieldInverterTest, require_that_word_with_NUL_byte_is_truncated)
{
    invertDocument(1, *makeCorruptDocument(_b, 7));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=after,a=1,"
              "w=before,a=1,"
              "w=corrupt,a=1,"
              "w=z,a=1",
              _inserter_backend.toStr());
}

TEST_F(FieldInverterTest, require_that_word_with_NUL_byte_is_dropped_when_truncated_to_zero_length)
{
    invertDocument(1, *makeCorruptDocument(_b, 0));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=after,a=1,"
              "w=before,a=1,"
              "w=z,a=1",
              _inserter_backend.toStr());
}

}
}

GTEST_MAIN_RUN_ALL_TESTS()
