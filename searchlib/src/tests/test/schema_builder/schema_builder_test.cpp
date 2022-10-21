// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::index::Schema;
using search::index::schema::CollectionType;
using search::test::DocBuilder;
using search::test::SchemaBuilder;

namespace {

auto add_all_fields = [](auto& header)
                      { using namespace document::config_builder;
                          using document::DataType;
                          header.addField("int8", DataType::T_BYTE)
                              .addField("int16", DataType::T_SHORT)
                              .addField("int32", DataType::T_INT)
                              .addField("int64", DataType::T_LONG)
                              .addField("bool", DataType::T_BOOL)
                              .addField("float", DataType::T_FLOAT)
                              .addField("double", DataType::T_DOUBLE)
                              .addField("string", DataType::T_STRING)
                              .addField("url",  DataType::T_URI)
                              .addTensorField("tensor", "tensor(x{},y{})")
                              .addField("int32_array", Array(DataType::T_INT))
                              .addField("int32_wset", Wset(DataType::T_INT))
                              .addField("url_array", Array(DataType::T_URI))
                              .addField("url_wset", Wset(DataType::T_URI));
                      };

}

class SchemaBuilderTest : public testing::Test
{
protected:
    Schema schema;

    SchemaBuilderTest();
    ~SchemaBuilderTest();
    void assert_index(vespalib::stringref name, search::index::schema::DataType exp_dt, CollectionType exp_ct);
    void assert_all_indexes();
    void assert_attribute(vespalib::stringref name, search::index::schema::DataType exp_dt, CollectionType exp_ct, const vespalib::string exp_tensor_spec = "");
    void assert_all_attributes();
};

SchemaBuilderTest::SchemaBuilderTest()
    : schema()
{
}

SchemaBuilderTest::~SchemaBuilderTest() = default;

void
SchemaBuilderTest::assert_index(vespalib::stringref name, search::index::schema::DataType exp_dt, CollectionType exp_ct)
{
    auto field_id = schema.getIndexFieldId(name);
    ASSERT_NE(Schema::UNKNOWN_FIELD_ID, field_id);
    auto& field = schema.getIndexField(field_id);
    EXPECT_EQ(exp_dt, field.getDataType());
    EXPECT_EQ(exp_ct, field.getCollectionType());
}

void
SchemaBuilderTest::assert_all_indexes()
{
    using DataType = search::index::schema::DataType;
    EXPECT_EQ(25u, schema.getNumIndexFields()); // string and url*, 1 + 3 * 8
    assert_index("string", DataType::STRING, CollectionType::SINGLE);
    assert_index("url", DataType::STRING, CollectionType::SINGLE);
    assert_index("url.scheme", DataType::STRING, CollectionType::SINGLE);
    assert_index("url_array", DataType::STRING, CollectionType::ARRAY);
    assert_index("url_wset", DataType::STRING, CollectionType::WEIGHTEDSET);
}

void
SchemaBuilderTest::assert_attribute(vespalib::stringref name, search::index::schema::DataType exp_dt, CollectionType exp_ct, const vespalib::string exp_tensor_spec)
{
    auto field_id = schema.getAttributeFieldId(name);
    ASSERT_NE(Schema::UNKNOWN_FIELD_ID, field_id);
    auto& field = schema.getAttributeField(field_id);
    EXPECT_EQ(exp_dt, field.getDataType());
    EXPECT_EQ(exp_ct, field.getCollectionType());
    EXPECT_EQ(exp_tensor_spec, field.get_tensor_spec());
}

void
SchemaBuilderTest::assert_all_attributes()
{
    using DataType = search::index::schema::DataType;
    EXPECT_EQ(11u, schema.getNumAttributeFields()); // all but url*, 14 - 3
    assert_attribute("int8", DataType::INT8, CollectionType::SINGLE);
    assert_attribute("int16", DataType::INT16, CollectionType::SINGLE);
    assert_attribute("int32", DataType::INT32, CollectionType::SINGLE);
    assert_attribute("int64", DataType::INT64, CollectionType::SINGLE);
    assert_attribute("bool", DataType::BOOL, CollectionType::SINGLE);
    assert_attribute("float", DataType::FLOAT, CollectionType::SINGLE);
    assert_attribute("double", DataType::DOUBLE, CollectionType::SINGLE);
    assert_attribute("string", DataType::STRING, CollectionType::SINGLE);
    assert_attribute("tensor", DataType::TENSOR, CollectionType::SINGLE, "tensor(x{},y{})");
    assert_attribute("int32_array", DataType::INT32, CollectionType::ARRAY);
    assert_attribute("int32_wset", DataType::INT32, CollectionType::WEIGHTEDSET);
}

TEST_F(SchemaBuilderTest, all_fields)
{
    DocBuilder db(add_all_fields);
    schema = SchemaBuilder(db).add_indexes({"string", "url", "url_array", "url_wset"}).add_attributes({"int8", "int16", "int32", "int64", "bool", "float", "double", "string", "tensor", "int32_array", "int32_wset"}).build();
    assert_all_indexes();
    assert_all_attributes();
}

TEST_F(SchemaBuilderTest, all_indexes_auto)
{
    DocBuilder db(add_all_fields);
    schema = SchemaBuilder(db).add_all_indexes().build();
    assert_all_indexes();
}

TEST_F(SchemaBuilderTest, all_attributes_auto)
{
    DocBuilder db(add_all_fields);
    schema = SchemaBuilder(db).add_all_attributes().build();
    assert_all_attributes();
}

GTEST_MAIN_RUN_ALL_TESTS()
