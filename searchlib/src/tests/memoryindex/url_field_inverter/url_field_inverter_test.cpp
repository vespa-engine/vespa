// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/memoryindex/url_field_inverter.h>
#include <vespa/document/datatype/urldatatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/index/empty_doc_builder.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/index/schema_index_fields.h>
#include <vespa/searchlib/index/string_field_builder.h>
#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter_backend.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace search {

using document::Document;
using document::ArrayFieldValue;
using document::StructFieldValue;
using document::UrlDataType;
using document::WeightedSetFieldValue;
using index::schema::CollectionType;
using index::schema::DataType;

using namespace index;


namespace memoryindex {

namespace {
const vespalib::string url = "url";

Document::UP
makeDoc10Single(EmptyDocBuilder &b)
{
    auto doc = b.make_document("id:ns:searchdocument::10");
    auto url_value = b.make_struct("url");
    StringFieldBuilder sfb(b);
    sfb.url_mode(true);
    url_value.setValue("all", sfb.tokenize("http://www.example.com:81/fluke?ab=2#4").build());
    url_value.setValue("scheme", sfb.tokenize("http").build());
    url_value.setValue("host", sfb.tokenize("www.example.com").build());
    url_value.setValue("port", sfb.tokenize("81").build());
    url_value.setValue("path", sfb.tokenize("/fluke").alt_word("altfluke").build());
    url_value.setValue("query", sfb.tokenize("ab=2").build());
    url_value.setValue("fragment", sfb.tokenize("4").build());
    doc->setValue("url", url_value);
    return doc;
}

Document::UP
makeDoc10Array(EmptyDocBuilder &b)
{
    auto doc = b.make_document("id:ns:searchdocument::10");
    StringFieldBuilder sfb(b);
    sfb.url_mode(true);
    auto url_array = b.make_array("url");
    auto url_value = b.make_url();
    url_value.setValue("all", sfb.tokenize("http://www.example.com:82/fluke?ab=2#8").build());
    url_value.setValue("scheme", sfb.tokenize("http").build());
    url_value.setValue("host", sfb.tokenize("www.example.com").build());
    url_value.setValue("port", sfb.tokenize("82").build());
    url_value.setValue("path", sfb.tokenize("/fluke").alt_word("altfluke").build());
    url_value.setValue("query", sfb.tokenize("ab=2").build());
    url_value.setValue("fragment", sfb.tokenize("8").build());
    url_array.add(url_value);
    url_value.setValue("all", sfb.tokenize("http://www.flickr.com:82/fluke?ab=2#9").build());
    url_value.setValue("scheme", sfb.tokenize("http").build());
    url_value.setValue("host", sfb.tokenize("www.flickr.com").build());
    url_value.setValue("path", sfb.tokenize("/fluke").build());
    url_value.setValue("fragment", sfb.tokenize("9").build());
    url_array.add(url_value);
    doc->setValue("url", url_array);
    return doc;
}

Document::UP
makeDoc10WeightedSet(EmptyDocBuilder &b)
{
    auto doc = b.make_document("id:ns:searchdocument::10");
    StringFieldBuilder sfb(b);
    sfb.url_mode(true);
    auto url_wset = b.make_wset("url");
    auto url_value = b.make_url();
    url_value.setValue("all", sfb.tokenize("http://www.example.com:83/fluke?ab=2#12").build());
    url_value.setValue("scheme", sfb.tokenize("http").build());
    url_value.setValue("host", sfb.tokenize("www.example.com").build());
    url_value.setValue("port", sfb.tokenize("83").build());
    url_value.setValue("path", sfb.tokenize("/fluke").alt_word("altfluke").build());
    url_value.setValue("query", sfb.tokenize("ab=2").build());
    url_value.setValue("fragment", sfb.tokenize("12").build());
    url_wset.add(url_value, 4);
    url_value.setValue("all", sfb.tokenize("http://www.flickr.com:85/fluke?ab=2#13").build());
    url_value.setValue("scheme", sfb.tokenize("http").build());
    url_value.setValue("host", sfb.tokenize("www.flickr.com").build());
    url_value.setValue("port", sfb.tokenize("85").build());
    url_value.setValue("path", sfb.tokenize("/fluke").build());
    url_value.setValue("query", sfb.tokenize("ab=2").build());
    url_value.setValue("fragment", sfb.tokenize("13").build());
    url_wset.add(url_value, 7);
    doc->setValue("url", url_wset);
    return doc;
}

Document::UP
makeDoc10Empty(EmptyDocBuilder &b)
{
    return b.make_document("id:ns:searchdocument::10");
}

}

struct UrlFieldInverterTest : public ::testing::Test {
    Schema _schema;
    EmptyDocBuilder _b;
    WordStore                       _word_store;
    FieldIndexRemover               _remover;
    test::OrderedFieldIndexInserterBackend _inserter_backend;
    FieldLengthCalculator           _calculator;
    std::vector<std::unique_ptr<IOrderedFieldIndexInserter>> _inserters;
    std::vector<std::unique_ptr<FieldInverter> > _inverters;
    std::unique_ptr<UrlFieldInverter> _urlInverter;
    index::SchemaIndexFields _schemaIndexFields;

    static Schema makeSchema(Schema::CollectionType collectionType) {
        Schema schema;
        schema.addUriIndexFields(Schema::IndexField("url", DataType::STRING, collectionType));
        return schema;
    }

    UrlFieldInverterTest(Schema::CollectionType collectionType,
                         EmptyDocBuilder::AddFieldsType add_fields)
        : _schema(makeSchema(collectionType)),
          _b(add_fields),
          _word_store(),
          _remover(_word_store),
          _inserter_backend(),
          _calculator(),
          _inserters(),
          _inverters(),
          _urlInverter(),
          _schemaIndexFields()
    {
        _schemaIndexFields.setup(_schema);
        for (uint32_t fieldId = 0; fieldId < _schema.getNumIndexFields();
             ++fieldId) {
            _inserters.emplace_back(std::make_unique<test::OrderedFieldIndexInserter>(_inserter_backend, fieldId));
            _inverters.push_back(std::make_unique<FieldInverter>(_schema,
                                                                 fieldId,
                                                                 _remover,
                                                                 *_inserters.back(),
                                                                 _calculator));
        }
        index::UriField &urlField =
            _schemaIndexFields._uriFields.front();
        _urlInverter = std::make_unique<UrlFieldInverter>
                       (collectionType,
                        _inverters[urlField._all].get(),
                        _inverters[urlField._scheme].get(),
                        _inverters[urlField._host].get(),
                        _inverters[urlField._port].get(),
                        _inverters[urlField._path].get(),
                        _inverters[urlField._query].get(),
                        _inverters[urlField._fragment].get(),
                        _inverters[urlField._hostname].get());
    }

    ~UrlFieldInverterTest() override;

    void invertDocument(uint32_t docId, const Document &doc) {
        _urlInverter->invertField(docId, doc.getValue(url));
    }

    void pushDocuments() {
        for (auto &inverter : _inverters) {
            inverter->pushDocuments();
        }
    }

    void enableAnnotations() {
        _urlInverter->setUseAnnotations(true);
    }
};

UrlFieldInverterTest::~UrlFieldInverterTest() = default;

EmptyDocBuilder::AddFieldsType
add_single_url = [](auto& header) {
                     header.addField("url", UrlDataType::getInstance().getId()); };

EmptyDocBuilder::AddFieldsType
add_array_url = [](auto& header) {
                    using namespace document::config_builder;
                    header.addField("url", Array(UrlDataType::getInstance().getId())); };

EmptyDocBuilder::AddFieldsType
add_wset_url = [](auto& header) {
                    using namespace document::config_builder;
                    header.addField("url", Wset(UrlDataType::getInstance().getId())); };



struct SingleInverterTest : public UrlFieldInverterTest {
    SingleInverterTest() : UrlFieldInverterTest(CollectionType::SINGLE, add_single_url) {}
};

struct ArrayInverterTest : public UrlFieldInverterTest {
    ArrayInverterTest() : UrlFieldInverterTest(CollectionType::ARRAY, add_array_url) {}
};

struct WeightedSetInverterTest : public UrlFieldInverterTest {
    WeightedSetInverterTest() : UrlFieldInverterTest(CollectionType::WEIGHTEDSET, add_wset_url) {}
};


TEST_F(SingleInverterTest, require_that_single_url_field_works)
{
    invertDocument(10, *makeDoc10Single(_b));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=2,a=10,"
              "w=4,a=10,"
              "w=81,a=10,"
              "w=ab,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=fluke,a=10,"
              "w=http,a=10,"
              "w=www,a=10,"
              "f=1,"
              "w=http,a=10,"
              "f=2,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=www,a=10,"
              "f=3,"
              "w=81,a=10,"
              "f=4,"
              "w=fluke,a=10,"
              "f=5,"
              "w=2,a=10,"
              "w=ab,a=10,"
              "f=6,"
              "w=4,a=10,"
              "f=7,"
              "w=EnDhOsT,a=10,"
              "w=StArThOsT,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=www,a=10",
              _inserter_backend.toStr());
}

TEST_F(ArrayInverterTest, require_that_array_url_field_works)
{
    invertDocument(10, *makeDoc10Array(_b));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=2,a=10,"
              "w=8,a=10,"
              "w=82,a=10,"
              "w=9,a=10,"
              "w=ab,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=fluke,a=10,"
              "w=http,a=10,"
              "w=www,a=10,"
              "f=1,"
              "w=http,a=10,"
              "f=2,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=www,a=10,"
              "f=3,"
              "w=82,a=10,"
              "f=4,"
              "w=fluke,a=10,"
              "f=5,"
              "w=2,a=10,"
              "w=ab,a=10,"
              "f=6,"
              "w=8,a=10,"
              "w=9,a=10,"
              "f=7,"
              "w=EnDhOsT,a=10,"
              "w=StArThOsT,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=www,a=10",
              _inserter_backend.toStr());
}

TEST_F(WeightedSetInverterTest, require_that_weighted_set_field_works)
{
    invertDocument(10, *makeDoc10WeightedSet(_b));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=12,a=10,"
              "w=13,a=10,"
              "w=2,a=10,"
              "w=83,a=10,"
              "w=85,a=10,"
              "w=ab,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=fluke,a=10,"
              "w=http,a=10,"
              "w=www,a=10,"
              "f=1,"
              "w=http,a=10,"
              "f=2,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=www,a=10,"
              "f=3,"
              "w=83,a=10,"
              "w=85,a=10,"
              "f=4,"
              "w=fluke,a=10,"
              "f=5,"
              "w=2,a=10,"
              "w=ab,a=10,"
              "f=6,"
              "w=12,a=10,"
              "w=13,a=10,"
              "f=7,"
              "w=EnDhOsT,a=10,"
              "w=StArThOsT,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=www,a=10",
              _inserter_backend.toStr());
}

TEST_F(SingleInverterTest, require_that_annotated_single_url_field_works)
{
    enableAnnotations();
    invertDocument(10, *makeDoc10Single(_b));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=2,a=10,"
              "w=4,a=10,"
              "w=81,a=10,"
              "w=ab,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=fluke,a=10,"
              "w=http,a=10,"
              "w=www,a=10,"
              "f=1,"
              "w=http,a=10,"
              "f=2,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=www,a=10,"
              "f=3,"
              "w=81,a=10,"
              "f=4,"
              "w=altfluke,a=10,"
              "w=fluke,a=10,"
              "f=5,"
              "w=2,a=10,"
              "w=ab,a=10,"
              "f=6,"
              "w=4,a=10,"
              "f=7,"
              "w=EnDhOsT,a=10,"
              "w=StArThOsT,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=www,a=10",
              _inserter_backend.toStr());
}

TEST_F(ArrayInverterTest, require_that_annotated_array_url_field_works)
{
    enableAnnotations();
    invertDocument(10, *makeDoc10Array(_b));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=2,a=10,"
              "w=8,a=10,"
              "w=82,a=10,"
              "w=9,a=10,"
              "w=ab,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=fluke,a=10,"
              "w=http,a=10,"
              "w=www,a=10,"
              "f=1,"
              "w=http,a=10,"
              "f=2,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=www,a=10,"
              "f=3,"
              "w=82,a=10,"
              "f=4,"
              "w=altfluke,a=10,"
              "w=fluke,a=10,"
              "f=5,"
              "w=2,a=10,"
              "w=ab,a=10,"
              "f=6,"
              "w=8,a=10,"
              "w=9,a=10,"
              "f=7,"
              "w=EnDhOsT,a=10,"
              "w=StArThOsT,a=10,"
              "w=com,a=10,"
              "w=example,a=10,"
              "w=flickr,a=10,"
              "w=www,a=10",
              _inserter_backend.toStr());
}

TEST_F(WeightedSetInverterTest, require_that_annotated_weighted_set_field_works)
{
    enableAnnotations();
    _inserter_backend.setVerbose();
    invertDocument(10, *makeDoc10WeightedSet(_b));
    pushDocuments();
    EXPECT_EQ("f=0,"
              "w=12,a=10(e=0,w=4,l=9[8]),"
              "w=13,a=10(e=1,w=7,l=9[8]),"
              "w=2,a=10(e=0,w=4,l=9[7],e=1,w=7,l=9[7]),"
              "w=83,a=10(e=0,w=4,l=9[4]),"
              "w=85,a=10(e=1,w=7,l=9[4]),"
              "w=ab,a=10(e=0,w=4,l=9[6],e=1,w=7,l=9[6]),"
              "w=com,a=10(e=0,w=4,l=9[3],e=1,w=7,l=9[3]),"
              "w=example,a=10(e=0,w=4,l=9[2]),"
              "w=flickr,a=10(e=1,w=7,l=9[2]),"
              "w=fluke,a=10(e=0,w=4,l=9[5],e=1,w=7,l=9[5]),"
              "w=http,a=10(e=0,w=4,l=9[0],e=1,w=7,l=9[0]),"
              "w=www,a=10(e=0,w=4,l=9[1],e=1,w=7,l=9[1]),"
              "f=1,"
              "w=http,a=10(e=0,w=4,l=1[0],e=1,w=7,l=1[0]),"
              "f=2,"
              "w=com,a=10(e=0,w=4,l=3[2],e=1,w=7,l=3[2]),"
              "w=example,a=10(e=0,w=4,l=3[1]),"
              "w=flickr,a=10(e=1,w=7,l=3[1]),"
              "w=www,a=10(e=0,w=4,l=3[0],e=1,w=7,l=3[0]),"
              "f=3,"
              "w=83,a=10(e=0,w=4,l=1[0]),"
              "w=85,a=10(e=1,w=7,l=1[0]),"
              "f=4,"
              "w=altfluke,a=10(e=0,w=4,l=1[0]),"
              "w=fluke,a=10(e=0,w=4,l=1[0],e=1,w=7,l=1[0]),"
              "f=5,"
              "w=2,a=10(e=0,w=4,l=2[1],e=1,w=7,l=2[1]),"
              "w=ab,a=10(e=0,w=4,l=2[0],e=1,w=7,l=2[0]),"
              "f=6,"
              "w=12,a=10(e=0,w=4,l=1[0]),"
              "w=13,a=10(e=1,w=7,l=1[0]),"
              "f=7,"
              "w=EnDhOsT,a=10(e=0,w=4,l=5[4],e=1,w=7,l=5[4]),"
              "w=StArThOsT,a=10(e=0,w=4,l=5[0],e=1,w=7,l=5[0]),"
              "w=com,a=10(e=0,w=4,l=5[3],e=1,w=7,l=5[3]),"
              "w=example,a=10(e=0,w=4,l=5[2]),"
              "w=flickr,a=10(e=1,w=7,l=5[2]),"
              "w=www,a=10(e=0,w=4,l=5[1],e=1,w=7,l=5[1])",
              _inserter_backend.toStr());
}

TEST_F(SingleInverterTest, require_that_empty_single_field_works)
{
    invertDocument(10, *makeDoc10Empty(_b));
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

TEST_F(ArrayInverterTest, require_that_empty_array_field_works)
{
    invertDocument(10, *makeDoc10Empty(_b));
    pushDocuments();
    EXPECT_EQ("",
              _inserter_backend.toStr());
}

TEST_F(WeightedSetInverterTest, require_that_empty_weighted_set_field_works)
{
    invertDocument(10, *makeDoc10Empty(_b));
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

TEST_F(SingleInverterTest, require_that_annotated_empty_single_field_works)
{
    enableAnnotations();
    invertDocument(10, *makeDoc10Empty(_b));
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

TEST_F(ArrayInverterTest, require_that_annotated_empty_array_field_works)
{
    enableAnnotations();
    invertDocument(10, *makeDoc10Empty(_b));
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

TEST_F(WeightedSetInverterTest, require_that_annotated_empty_weighted_set_field_works)
{
    enableAnnotations();
    invertDocument(10, *makeDoc10Empty(_b));
    pushDocuments();
    EXPECT_EQ("", _inserter_backend.toStr());
}

}
}

GTEST_MAIN_RUN_ALL_TESTS()
