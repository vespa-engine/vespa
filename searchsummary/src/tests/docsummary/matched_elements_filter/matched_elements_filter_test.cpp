// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue//document.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchsummary/docsummary/docsum_store_document.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/searchsummary/docsummary/matched_elements_filter_dfw.h>
#include <vespa/searchsummary/docsummary/resultclass.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("matched_elements_filter_test");

using search::AttributeFactory;
using search::AttributeVector;
using search::MatchingElements;
using search::MatchingElementsFields;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::docsummary::IDocsumStoreDocument;
using search::docsummary::DocsumStoreDocument;
using search::docsummary::test::SlimeValue;
using vespalib::Slime;

using namespace document;
using namespace search::docsummary;
using namespace vespalib::slime;

using ElementVector = std::vector<uint32_t>;

StructDataType::UP
make_struct_elem_type()
{
    auto result = std::make_unique<StructDataType>("elem");
    result->addField(Field("name", *DataType::STRING));
    result->addField(Field("weight", *DataType::INT));
    return result;
}

constexpr uint32_t class_id = 3;
constexpr uint32_t doc_id = 2;

class DocsumStore {
private:
    ResultConfig _config;
    DocumentType _doc_type;
    StructDataType::UP _elem_type;
    ArrayDataType _array_type;
    MapDataType _map_type;
    WeightedSetDataType _wset_type;
    bool _empty_values;
    bool _skip_set_values;

    StructFieldValue::UP make_elem_value(const std::string& name, int weight) const {
        auto result = std::make_unique<StructFieldValue>(*_elem_type);
        result->setValue("name", StringFieldValue(name));
        result->setValue("weight", IntFieldValue(weight));
        return result;
    }

public:
    DocsumStore()
        : _config(),
          _doc_type("test"),
          _elem_type(make_struct_elem_type()),
          _array_type(*_elem_type),
          _map_type(*DataType::STRING, *_elem_type),
          _wset_type(*DataType::STRING, false, false),
          _empty_values(false),
          _skip_set_values(false)
    {
        _doc_type.addField(Field("array", _array_type));
        _doc_type.addField(Field("map", _map_type));
        _doc_type.addField(Field("map2", _map_type));
        _doc_type.addField(Field("wset", _wset_type));

        auto* result_class = _config.addResultClass("test", class_id);
        EXPECT_TRUE(result_class->addConfigEntry("array"));
        EXPECT_TRUE(result_class->addConfigEntry("map"));
        EXPECT_TRUE(result_class->addConfigEntry("map2"));
    }
    ~DocsumStore();
    std::unique_ptr<IDocsumStoreDocument> getMappedDocsum() {
        auto doc = Document::make_without_repo(_doc_type, DocumentId("id:test:test::0"));
        {
            ArrayFieldValue array_value(_array_type);
            if (!_empty_values) {
                array_value.append(make_elem_value("a", 3));
                array_value.append(make_elem_value("b", 5));
                array_value.append(make_elem_value("c", 7));
            }
            if (!_skip_set_values) {
                doc->setValue("array", array_value);
            }
        }
        {
            MapFieldValue map_value(_map_type);
            if (!_empty_values) {
                map_value.put(StringFieldValue("a"), *make_elem_value("a", 3));
                map_value.put(StringFieldValue("b"), *make_elem_value("b", 5));
                map_value.put(StringFieldValue("c"), *make_elem_value("c", 7));
            }
            if (!_skip_set_values) {
                doc->setValue("map", map_value);
            }
        }
        {
            MapFieldValue map2_value(_map_type);
            if (!_empty_values) {
                map2_value.put(StringFieldValue("dummy"), *make_elem_value("dummy", 2));
            }
            if (!_skip_set_values) {
                doc->setValue("map2", map2_value);
            }
        }
        {
            WeightedSetFieldValue wset_value(_wset_type);
            if (!_empty_values) {
                wset_value.add(StringFieldValue("a"), 13);
                wset_value.add(StringFieldValue("b"), 15);
                wset_value.add(StringFieldValue("c"), 17);
            }
            if (!_skip_set_values) {
                doc->setValue("wset", wset_value);
            }
        }
        return std::make_unique<DocsumStoreDocument>(std::move(doc));
    }
    void set_empty_values() { _empty_values = true; }
    void set_skip_set_values() { _skip_set_values = true; }
};

DocsumStore::~DocsumStore() = default;

class AttributeContext : public IAttributeContext {
private:
    AttributeVector::SP _map_value_name;
    AttributeVector::SP _map2_key;
    AttributeVector::SP _array_weight;
public:
    AttributeContext()
        : _map_value_name(AttributeFactory::createAttribute("map.value.name", Config(BasicType::STRING, CollectionType::ARRAY))),
          _map2_key(AttributeFactory::createAttribute("map2.key", Config(BasicType::STRING, CollectionType::ARRAY))),
          _array_weight(AttributeFactory::createAttribute("array.weight", Config(BasicType::INT32, CollectionType::ARRAY)))
    {}
    ~AttributeContext() override;
    const IAttributeVector* getAttribute(const string&) const override { abort(); }
    const IAttributeVector* getAttributeStableEnum(const string&) const override { abort(); }
    void getAttributeList(std::vector<const IAttributeVector*>& list) const override {
        list.push_back(_map_value_name.get());
        list.push_back(_map2_key.get());
        list.push_back(_array_weight.get());
    }
    void releaseEnumGuards() override { abort(); }
    void asyncForAttribute(const vespalib::string&, std::unique_ptr<search::attribute::IAttributeFunctor>) const override { abort(); }
};

AttributeContext::~AttributeContext() = default;

class StateCallback : public GetDocsumsStateCallback {
private:
    std::string _field_name;
    ElementVector _matching_elements;

public:
    StateCallback(const std::string& field_name, const ElementVector& matching_elements)
        : _field_name(field_name),
          _matching_elements(matching_elements)
    {
    }
    ~StateCallback() override;
    void fillSummaryFeatures(GetDocsumsState&) override {}
    void fillRankFeatures(GetDocsumsState&) override {}
    std::unique_ptr<MatchingElements> fill_matching_elements(const MatchingElementsFields&) override {
        auto result = std::make_unique<MatchingElements>();
        result->add_matching_elements(doc_id, _field_name, _matching_elements);
        return result;
    }
};

StateCallback::~StateCallback() = default;

class MatchedElementsFilterTest : public ::testing::Test {
private:
    DocsumStore _doc_store;
    AttributeContext _attr_ctx;
    std::shared_ptr<MatchingElementsFields> _fields;

    Slime run_filter_field_writer(const std::string& input_field_name, const ElementVector& matching_elements) {
        auto writer = make_field_writer(input_field_name);
        auto doc = _doc_store.getMappedDocsum();
        StateCallback callback(input_field_name, matching_elements);
        GetDocsumsState state(callback);
        Slime slime;
        SlimeInserter inserter(slime);

        writer->insertField(doc_id, doc.get(), state, inserter);
        return slime;
    }

public:
    MatchedElementsFilterTest()
        : _doc_store(),
          _attr_ctx(),
          _fields(std::make_shared<MatchingElementsFields>())
    {
    }
    ~MatchedElementsFilterTest() override;
    std::unique_ptr<DocsumFieldWriter> make_field_writer(const std::string& input_field_name) {
        return MatchedElementsFilterDFW::create(input_field_name,_attr_ctx, _fields);
    }
    void expect_filtered(const std::string& input_field_name, const ElementVector& matching_elements, const std::string& exp_slime_as_json) {
        Slime act = run_filter_field_writer(input_field_name, matching_elements);
        SlimeValue exp(exp_slime_as_json);
        EXPECT_EQ(exp.slime, act);
    }
    const MatchingElementsFields& fields() const { return *_fields; }
    void set_empty_values() { _doc_store.set_empty_values(); }
    void set_skip_set_values() { _doc_store.set_skip_set_values(); }
 };

MatchedElementsFilterTest::~MatchedElementsFilterTest() = default;

TEST_F(MatchedElementsFilterTest, filters_elements_in_array_field_value)
{
    expect_filtered("array", {}, "null");
    expect_filtered("array", {0}, "[{'name':'a','weight':3}]");
    expect_filtered("array", {1}, "[{'name':'b','weight':5}]");
    expect_filtered("array", {2}, "[{'name':'c','weight':7}]");
    expect_filtered("array", {0, 1, 2}, "[{'name':'a','weight':3},"
                                        "{'name':'b','weight':5},"
                                        "{'name':'c','weight':7}]");
    expect_filtered("array", {0, 1, 100}, "null");
    set_empty_values();
    expect_filtered("array", {}, "null");
    set_skip_set_values();
    expect_filtered("array", {}, "null");
}

TEST_F(MatchedElementsFilterTest, matching_elements_fields_is_setup_for_array_field_value)
{
    auto writer = make_field_writer("array");
    EXPECT_TRUE(fields().has_field("array"));
    EXPECT_EQ("", fields().get_enclosing_field("array.name"));
    EXPECT_EQ("array", fields().get_enclosing_field("array.weight"));
}

TEST_F(MatchedElementsFilterTest, filters_elements_in_map_field_value)
{
    expect_filtered("map", {}, "null");
    expect_filtered("map", {0}, "[{'key':'a','value':{'name':'a','weight':3}}]");
    expect_filtered("map", {1}, "[{'key':'b','value':{'name':'b','weight':5}}]");
    expect_filtered("map", {2}, "[{'key':'c','value':{'name':'c','weight':7}}]");
    expect_filtered("map", {0, 1, 2}, "[{'key':'a','value':{'name':'a','weight':3}},"
                                      "{'key':'b','value':{'name':'b','weight':5}},"
                                      "{'key':'c','value':{'name':'c','weight':7}}]");
    expect_filtered("map", {0, 1, 100}, "null");
    set_empty_values();
    expect_filtered("map", {}, "null");
    set_skip_set_values();
    expect_filtered("map", {}, "null");
}

TEST_F(MatchedElementsFilterTest, filter_elements_in_weighed_set_field_value)
{
    expect_filtered("wset", {}, "null");
    expect_filtered("wset", {0}, "[{'item':'a','weight':13}]");
    expect_filtered("wset", {1}, "[{'item':'b','weight':15}]");
    expect_filtered("wset", {2}, "[{'item':'c','weight':17}]");
    expect_filtered("wset", {0, 1, 2}, "[{'item':'a','weight':13},{'item':'b','weight':15},{'item':'c','weight':17}]");
    expect_filtered("wset", {0, 1, 100}, "null");
    set_empty_values();
    expect_filtered("wset", {}, "null");
    set_skip_set_values();
    expect_filtered("wset", {}, "null");
}

TEST_F(MatchedElementsFilterTest, matching_elements_fields_is_setup_for_map_field_value)
{
    {
        auto writer = make_field_writer("map");
        EXPECT_TRUE(fields().has_field("map"));
        EXPECT_EQ("", fields().get_enclosing_field("map.key"));
        EXPECT_EQ("map", fields().get_enclosing_field("map.value.name"));
        EXPECT_EQ("", fields().get_enclosing_field("map.value.weight"));
    }
    {
        auto writer = make_field_writer("map2");
        EXPECT_TRUE(fields().has_field("map2"));
        EXPECT_EQ("map2", fields().get_enclosing_field("map2.key"));
        EXPECT_EQ("", fields().get_enclosing_field("map2.value.name"));
        EXPECT_EQ("", fields().get_enclosing_field("map2.value.weight"));
    }
}

TEST_F(MatchedElementsFilterTest, field_writer_is_not_generated_as_it_depends_on_data_from_document_store)
{
    auto writer = make_field_writer("array");
    EXPECT_FALSE(writer->isGenerated());
}

GTEST_MAIN_RUN_ALL_TESTS()
