// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/document.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/struct_field_mapper.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/searchsummary/docsummary/matched_elements_filter_dfw.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchsummary/docsummary/resultpacker.h>
#include <vespa/searchsummary/docsummary/summaryfieldconverter.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("matched_elements_filter_test");

using search::AttributeFactory;
using search::AttributeVector;
using search::MatchingElements;
using search::StructFieldMapper;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using vespalib::Slime;

using namespace document;
using namespace search::docsummary;
using namespace vespalib::slime;

using ElementVector = std::vector<uint32_t>;

struct SlimeValue {
    Slime slime;

    SlimeValue(const std::string& json_input)
        : slime()
    {
        size_t used = JsonFormat::decode(json_input, slime);
        EXPECT_GT(used, 0);
    }
};

StructDataType::UP
make_struct_elem_type()
{
    auto result = std::make_unique<StructDataType>("elem");
    result->addField(Field("name", *DataType::STRING, true));
    result->addField(Field("weight", *DataType::INT, true));
    return result;
}

constexpr uint32_t class_id = 3;
constexpr uint32_t doc_id = 2;

class DocsumStore {
private:
    ResultConfig _config;
    ResultPacker _packer;
    DocumentType _doc_type;
    StructDataType::UP _elem_type;
    ArrayDataType _array_type;
    MapDataType _map_type;

    StructFieldValue::UP make_elem_value(const std::string& name, int weight) const {
        auto result = std::make_unique<StructFieldValue>(*_elem_type);
        result->setValue("name", StringFieldValue(name));
        result->setValue("weight", IntFieldValue(weight));
        return result;
    }

    void write_field_value(const FieldValue& value) {
        auto converted = SummaryFieldConverter::convertSummaryField(false, value);
        const auto* raw_field = dynamic_cast<const RawFieldValue*>(converted.get());
        ASSERT_TRUE(raw_field);
        auto raw_buf = raw_field->getAsRaw();
        bool result = _packer.AddLongString(raw_buf.first, raw_buf.second);
        ASSERT_TRUE(result);
    }

public:
    DocsumStore()
        : _config(),
          _packer(&_config),
          _doc_type("test"),
          _elem_type(make_struct_elem_type()),
          _array_type(*_elem_type),
          _map_type(*DataType::STRING, *_elem_type)
    {
        _doc_type.addField(Field("array_in_doc", _array_type, true));
        _doc_type.addField(Field("map_in_doc", _map_type, true));

        auto* result_class = _config.AddResultClass("test", class_id);
        EXPECT_TRUE(result_class->AddConfigEntry("array", ResType::RES_JSONSTRING));
        EXPECT_TRUE(result_class->AddConfigEntry("map", ResType::RES_JSONSTRING));
        EXPECT_TRUE(result_class->AddConfigEntry("map2", ResType::RES_JSONSTRING));
        _config.CreateEnumMaps();
    }
    ~DocsumStore() {}
    const ResultConfig& get_config() const { return _config; }
    const ResultClass* get_class() const { return _config.LookupResultClass(class_id); }
    search::docsummary::DocsumStoreValue getMappedDocsum() {
        assert(_packer.Init(class_id));
        auto doc = std::make_unique<Document>(_doc_type, DocumentId("id:test:test::0"));
        {
            ArrayFieldValue array_value(_array_type);
            array_value.append(make_elem_value("a", 3));
            array_value.append(make_elem_value("b", 5));
            array_value.append(make_elem_value("c", 7));
            write_field_value(array_value);
            doc->setValue("array_in_doc", array_value);
        }
        {
            MapFieldValue map_value(_map_type);
            map_value.put(StringFieldValue("a"), *make_elem_value("a", 3));
            map_value.put(StringFieldValue("b"), *make_elem_value("b", 5));
            map_value.put(StringFieldValue("c"), *make_elem_value("c", 7));
            write_field_value(map_value);
            doc->setValue("map_in_doc", map_value);
        }
        {
            MapFieldValue map2_value(_map_type);
            map2_value.put(StringFieldValue("dummy"), *make_elem_value("dummy", 2));
            write_field_value(map2_value);
        }
        const char* buf;
        uint32_t buf_len;
        assert(_packer.GetDocsumBlob(&buf, &buf_len));
        return DocsumStoreValue(buf, buf_len, std::move(doc));
    }
};

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
    ~AttributeContext() {}
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
    ~StateCallback() {}
    void FillSummaryFeatures(GetDocsumsState*, IDocsumEnvironment*) override {}
    void FillRankFeatures(GetDocsumsState*, IDocsumEnvironment*) override {}
    void ParseLocation(GetDocsumsState*) override {}
    std::unique_ptr<MatchingElements> fill_matching_elements(const StructFieldMapper&) override {
        auto result = std::make_unique<MatchingElements>();
        result->add_matching_elements(doc_id, _field_name, _matching_elements);
        return result;
    }
};

class MatchedElementsFilterTest : public ::testing::Test {
private:
    DocsumStore _doc_store;
    AttributeContext _attr_ctx;
    std::shared_ptr<StructFieldMapper> _mapper;

    Slime run_filter_field_writer(const std::string& input_field_name, const ElementVector& matching_elements) {
        auto writer = make_field_writer(input_field_name);
        GeneralResult result(_doc_store.get_class());
        auto docsum = _doc_store.getMappedDocsum();
        result.inplaceUnpack(docsum);
        StateCallback callback(input_field_name, matching_elements);
        GetDocsumsState state(callback);
        Slime slime;
        SlimeInserter inserter(slime);

        writer->insertField(doc_id, &result, &state, ResType::RES_JSONSTRING, inserter);
        return slime;
    }

public:
    MatchedElementsFilterTest()
        : _doc_store(),
          _attr_ctx(),
          _mapper(std::make_shared<StructFieldMapper>())
    {
    }
    ~MatchedElementsFilterTest() {}
    std::unique_ptr<IDocsumFieldWriter> make_field_writer(const std::string& input_field_name) {
        int input_field_enum = _doc_store.get_config().GetFieldNameEnum().Lookup(input_field_name.c_str());
        return MatchedElementsFilterDFW::create(input_field_name, input_field_enum,
                                                _attr_ctx, _mapper);
    }
    void expect_filtered(const std::string& input_field_name, const ElementVector& matching_elements, const std::string& exp_slime_as_json) {
        Slime act = run_filter_field_writer(input_field_name, matching_elements);
        SlimeValue exp(exp_slime_as_json);
        EXPECT_EQ(exp.slime, act);
    }
    const StructFieldMapper& mapper() const { return *_mapper; }
};

TEST_F(MatchedElementsFilterTest, filters_elements_in_array_field_value)
{
    expect_filtered("array", {}, "[]");
    expect_filtered("array", {0}, "[{'name':'a','weight':3}]");
    expect_filtered("array", {1}, "[{'name':'b','weight':5}]");
    expect_filtered("array", {2}, "[{'name':'c','weight':7}]");
    expect_filtered("array", {0, 1, 2}, "[{'name':'a','weight':3},"
                                        "{'name':'b','weight':5},"
                                        "{'name':'c','weight':7}]");
}

TEST_F(MatchedElementsFilterTest, filters_elements_in_array_field_value_when_input_field_is_not_in_docsum_blob)
{
    expect_filtered("array_in_doc", {}, "[]");
    expect_filtered("array_in_doc", {0}, "[{'name':'a','weight':3}]");
    expect_filtered("array_in_doc", {1}, "[{'name':'b','weight':5}]");
    expect_filtered("array_in_doc", {2}, "[{'name':'c','weight':7}]");
    expect_filtered("array_in_doc", {0, 1, 2}, "[{'name':'a','weight':3},"
                                               "{'name':'b','weight':5},"
                                               "{'name':'c','weight':7}]");
}

TEST_F(MatchedElementsFilterTest, struct_field_mapper_is_setup_for_array_field_value)
{
    auto writer = make_field_writer("array");
    EXPECT_TRUE(mapper().is_struct_field("array"));
    EXPECT_EQ("", mapper().get_struct_field("array.name"));
    EXPECT_EQ("array", mapper().get_struct_field("array.weight"));
}

TEST_F(MatchedElementsFilterTest, filters_elements_in_map_field_value)
{
    expect_filtered("map", {}, "[]");
    expect_filtered("map", {0}, "[{'key':'a','value':{'name':'a','weight':3}}]");
    expect_filtered("map", {1}, "[{'key':'b','value':{'name':'b','weight':5}}]");
    expect_filtered("map", {2}, "[{'key':'c','value':{'name':'c','weight':7}}]");
    expect_filtered("map", {0, 1, 2}, "[{'key':'a','value':{'name':'a','weight':3}},"
                                      "{'key':'b','value':{'name':'b','weight':5}},"
                                      "{'key':'c','value':{'name':'c','weight':7}}]");
}

TEST_F(MatchedElementsFilterTest, filters_elements_in_map_field_value_when_input_field_is_not_in_docsum_blob)
{
    expect_filtered("map_in_doc", {}, "[]");
    expect_filtered("map_in_doc", {0}, "[{'key':'a','value':{'name':'a','weight':3}}]");
    expect_filtered("map_in_doc", {1}, "[{'key':'b','value':{'name':'b','weight':5}}]");
    expect_filtered("map_in_doc", {2}, "[{'key':'c','value':{'name':'c','weight':7}}]");
    expect_filtered("map_in_doc", {0, 1, 2}, "[{'key':'a','value':{'name':'a','weight':3}},"
                                             "{'key':'b','value':{'name':'b','weight':5}},"
                                             "{'key':'c','value':{'name':'c','weight':7}}]");
}

TEST_F(MatchedElementsFilterTest, struct_field_mapper_is_setup_for_map_field_value)
{
    {
        auto writer = make_field_writer("map");
        EXPECT_TRUE(mapper().is_struct_field("map"));
        EXPECT_EQ("", mapper().get_struct_field("map.key"));
        EXPECT_EQ("map", mapper().get_struct_field("map.value.name"));
        EXPECT_EQ("", mapper().get_struct_field("map.value.weight"));
    }
    {
        auto writer = make_field_writer("map2");
        EXPECT_TRUE(mapper().is_struct_field("map2"));
        EXPECT_EQ("map2", mapper().get_struct_field("map2.key"));
        EXPECT_EQ("", mapper().get_struct_field("map2.value.name"));
        EXPECT_EQ("", mapper().get_struct_field("map2.value.weight"));
    }
}

TEST_F(MatchedElementsFilterTest, field_writer_is_not_generated_as_it_depends_on_data_from_document_store)
{
    auto writer = make_field_writer("array");
    EXPECT_FALSE(writer->IsGenerated());
}

GTEST_MAIN_RUN_ALL_TESTS()
