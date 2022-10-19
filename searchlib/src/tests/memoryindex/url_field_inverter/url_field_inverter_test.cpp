// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/memoryindex/url_field_inverter.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/index/schema_index_fields.h>
#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter_backend.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace search {

using document::Document;
using document::ArrayFieldValue;
using document::StringFieldValue;
using document::WeightedSetFieldValue;
using index::schema::CollectionType;
using index::schema::DataType;
using search::test::DocBuilder;

using namespace index;


namespace memoryindex {

namespace {
const vespalib::string url = "url";

Document::UP
makeDoc10Single(DocBuilder &b)
{
    auto doc = b.make_document("id:ns:searchdocument::10");
    doc->setValue("url", StringFieldValue("http://www.example.com:81/fluke?ab=2#4"));
    return doc;
}

Document::UP
makeDoc10Array(DocBuilder &b)
{
    auto doc = b.make_document("id:ns:searchdocument::10");
    auto url_array = b.make_array("url");
    url_array.add(StringFieldValue("http://www.example.com:82/fluke?ab=2#8"));
    url_array.add(StringFieldValue("http://www.flickr.com:82/fluke?ab=2#9"));
    doc->setValue("url", url_array);
    return doc;
}

Document::UP
makeDoc10WeightedSet(DocBuilder &b)
{
    auto doc = b.make_document("id:ns:searchdocument::10");
    auto url_wset = b.make_wset("url");
    url_wset.add(StringFieldValue("http://www.example.com:83/fluke?ab=2#12"), 4);
    url_wset.add(StringFieldValue("http://www.flickr.com:85/fluke?ab=2#13"), 7);
    doc->setValue("url", url_wset);
    return doc;
}

Document::UP
makeDoc10Empty(DocBuilder &b)
{
    return b.make_document("id:ns:searchdocument::10");
}

}

struct UrlFieldInverterTest : public ::testing::Test {
    Schema _schema;
    DocBuilder _b;
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
                         DocBuilder::AddFieldsType add_fields)
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
};

UrlFieldInverterTest::~UrlFieldInverterTest() = default;

DocBuilder::AddFieldsType
add_single_url = [](auto& header) {
                     header.addField("url", document::DataType::T_URI); };

DocBuilder::AddFieldsType
add_array_url = [](auto& header) {
                    using namespace document::config_builder;
                    header.addField("url", Array(document::DataType::T_URI)); };

DocBuilder::AddFieldsType
add_wset_url = [](auto& header) {
                    using namespace document::config_builder;
                    header.addField("url", Wset(document::DataType::T_URI)); };



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

}
}

GTEST_MAIN_RUN_ALL_TESTS()
