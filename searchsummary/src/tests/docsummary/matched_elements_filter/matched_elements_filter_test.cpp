// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/document.h>
#include <vespa/searchlib/common/matching_elements.h>
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

using search::MatchingElements;
using search::StructFieldMapper;
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
    SlimeValue(const Slime& slime_with_raw_field)
        : slime()
    {
        size_t used = BinaryFormat::decode(slime_with_raw_field.get().asString(), slime);
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
          _elem_type(make_struct_elem_type()),
          _array_type(*_elem_type),
          _map_type(*DataType::STRING, *_elem_type)
    {
        auto* result_class = _config.AddResultClass("test", class_id);
        EXPECT_TRUE(result_class->AddConfigEntry("array", ResType::RES_JSONSTRING));
        EXPECT_TRUE(result_class->AddConfigEntry("map", ResType::RES_JSONSTRING));
        _config.CreateEnumMaps();
    }
    const ResultConfig& get_config() const { return _config; }
    const ResultClass* get_class() const { return _config.LookupResultClass(class_id); }
    search::docsummary::DocsumStoreValue getMappedDocsum() {
        assert(_packer.Init(class_id));
        {
            ArrayFieldValue array_value(_array_type);
            array_value.append(make_elem_value("a", 3));
            array_value.append(make_elem_value("b", 5));
            array_value.append(make_elem_value("c", 7));
            write_field_value(array_value);
        }
        {
            MapFieldValue map_value(_map_type);
            map_value.put(StringFieldValue("a"), *make_elem_value("a", 3));
            map_value.put(StringFieldValue("b"), *make_elem_value("b", 5));
            map_value.put(StringFieldValue("c"), *make_elem_value("c", 7));
            write_field_value(map_value);
        }
        const char* buf;
        uint32_t buf_len;
        assert(_packer.GetDocsumBlob(&buf, &buf_len));
        return DocsumStoreValue(buf, buf_len);
    }
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
    DocsumStore _store;

    SlimeValue run_filter_field_writer(const std::string& input_field_name, const ElementVector& matching_elements) {
        int input_field_enum = _store.get_config().GetFieldNameEnum().Lookup(input_field_name.c_str());
        EXPECT_GE(input_field_enum, 0);
        MatchedElementsFilterDFW filter(input_field_name, input_field_enum);

        GeneralResult result(_store.get_class());
        result.inplaceUnpack(_store.getMappedDocsum());
        StateCallback callback(input_field_name, matching_elements);
        GetDocsumsState state(callback);
        Slime slime;
        SlimeInserter inserter(slime);

        filter.insertField(doc_id, &result, &state, ResType::RES_JSONSTRING, inserter);
        return SlimeValue(slime);
    }

public:
    MatchedElementsFilterTest()
        : _store()
    {
    }
    void expect_filtered(const std::string& input_field_name, const ElementVector& matching_elements, const std::string& exp_slime_as_json) {
        SlimeValue act = run_filter_field_writer(input_field_name, matching_elements);
        SlimeValue exp(exp_slime_as_json);
        EXPECT_EQ(exp.slime, act.slime);
    }
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

TEST_F(MatchedElementsFilterTest, field_writer_is_not_generated_as_it_depends_on_data_from_document_store)
{
    MatchedElementsFilterDFW filter("foo", 0);
    EXPECT_FALSE(filter.IsGenerated());
}

GTEST_MAIN_RUN_ALL_TESTS()
