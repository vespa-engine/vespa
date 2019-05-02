// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/stllike/string.h>
#include <fstream>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configparser.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/log/log.h>
LOG_SETUP("schema_test");

using vespalib::string;

namespace search::index {

using schema::DataType;
using schema::CollectionType;
using SIAF = Schema::ImportedAttributeField;

void assertField(const Schema::Field & exp, const Schema::Field & act) {
    EXPECT_EQUAL(exp.getName(), act.getName());
    EXPECT_EQUAL(exp.getDataType(), act.getDataType());
    EXPECT_EQUAL(exp.getCollectionType(), act.getCollectionType());
}

void assertIndexField(const Schema::IndexField & exp,
                      const Schema::IndexField & act)
{
    assertField(exp, act);
    EXPECT_EQUAL(exp.getAvgElemLen(), act.getAvgElemLen());
}

void assertSet(const Schema::FieldSet &exp,
               const Schema::FieldSet &act)
{
    EXPECT_EQUAL(exp.getName(), act.getName());
    ASSERT_EQUAL(exp.getFields().size(), act.getFields().size());
    for (size_t i = 0; i < exp.getFields().size(); ++i) {
        EXPECT_EQUAL(exp.getFields()[i], act.getFields()[i]);
    }
}

void assertSchema(const Schema & exp, const Schema & act) {
    ASSERT_EQUAL(exp.getNumIndexFields(), act.getNumIndexFields());
    for (size_t i = 0; i < exp.getNumIndexFields(); ++i) {
        assertIndexField(exp.getIndexField(i), act.getIndexField(i));
    }
    ASSERT_EQUAL(exp.getNumAttributeFields(), act.getNumAttributeFields());
    for (size_t i = 0; i < exp.getNumAttributeFields(); ++i) {
        assertField(exp.getAttributeField(i), act.getAttributeField(i));
    }
    ASSERT_EQUAL(exp.getNumSummaryFields(), act.getNumSummaryFields());
    for (size_t i = 0; i < exp.getNumSummaryFields(); ++i) {
        assertField(exp.getSummaryField(i), act.getSummaryField(i));
    }
    ASSERT_EQUAL(exp.getNumFieldSets(), act.getNumFieldSets());
    for (size_t i = 0; i < exp.getNumFieldSets(); ++i) {
        assertSet(exp.getFieldSet(i), act.getFieldSet(i));
    }
    const auto &expImported = exp.getImportedAttributeFields();
    const auto &actImported = act.getImportedAttributeFields();
    ASSERT_EQUAL(expImported.size(), actImported.size());
    for (size_t i = 0; i < expImported.size(); ++i) {
        assertField(expImported[i], actImported[i]);
    }
}

TEST("testBasic") {
    Schema s;
    EXPECT_EQUAL(0u, s.getNumIndexFields());
    EXPECT_EQUAL(0u, s.getNumAttributeFields());
    EXPECT_EQUAL(0u, s.getNumSummaryFields());
    EXPECT_EQUAL(0u, s.getNumImportedAttributeFields());

    s.addIndexField(Schema::IndexField("foo", DataType::STRING));
    s.addIndexField(Schema::IndexField("bar", DataType::INT32));

    s.addAttributeField(Schema::AttributeField("foo", DataType::STRING, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("bar", DataType::INT32,  CollectionType::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("cox", DataType::STRING));

    s.addSummaryField(Schema::SummaryField("foo", DataType::STRING, CollectionType::ARRAY));
    s.addSummaryField(Schema::SummaryField("bar", DataType::INT32,  CollectionType::WEIGHTEDSET));
    s.addSummaryField(Schema::SummaryField("cox", DataType::STRING));
    s.addSummaryField(Schema::SummaryField("fox", DataType::RAW));

    s.addFieldSet(Schema::FieldSet("default").addField("foo").addField("bar"));

    s.addImportedAttributeField(SIAF("imported", DataType::INT32));

    EXPECT_EQUAL(2u, s.getNumIndexFields());
    {
        EXPECT_EQUAL("foo", s.getIndexField(0).getName());
        EXPECT_EQUAL(DataType::STRING, s.getIndexField(0).getDataType());
        EXPECT_EQUAL(CollectionType::SINGLE, s.getIndexField(0).getCollectionType());

        EXPECT_EQUAL("bar", s.getIndexField(1).getName());
        EXPECT_EQUAL(DataType::INT32, s.getIndexField(1).getDataType());
        EXPECT_EQUAL(CollectionType::SINGLE, s.getIndexField(1).getCollectionType());

        EXPECT_EQUAL(0u, s.getIndexFieldId("foo"));
        EXPECT_EQUAL(1u, s.getIndexFieldId("bar"));
        EXPECT_EQUAL(Schema::UNKNOWN_FIELD_ID, s.getIndexFieldId("cox"));
    }
    EXPECT_EQUAL(3u, s.getNumAttributeFields());
    {
        EXPECT_EQUAL("foo", s.getAttributeField(0).getName());
        EXPECT_EQUAL(DataType::STRING, s.getAttributeField(0).getDataType());
        EXPECT_EQUAL(CollectionType::ARRAY, s.getAttributeField(0).getCollectionType());

        EXPECT_EQUAL("bar", s.getAttributeField(1).getName());
        EXPECT_EQUAL(DataType::INT32, s.getAttributeField(1).getDataType());
        EXPECT_EQUAL(CollectionType::WEIGHTEDSET, s.getAttributeField(1).getCollectionType());

        EXPECT_EQUAL("cox", s.getAttributeField(2).getName());
        EXPECT_EQUAL(DataType::STRING, s.getAttributeField(2).getDataType());
        EXPECT_EQUAL(CollectionType::SINGLE, s.getAttributeField(2).getCollectionType());

        EXPECT_EQUAL(0u, s.getAttributeFieldId("foo"));
        EXPECT_EQUAL(1u, s.getAttributeFieldId("bar"));
        EXPECT_EQUAL(2u, s.getAttributeFieldId("cox"));
        EXPECT_EQUAL(Schema::UNKNOWN_FIELD_ID, s.getIndexFieldId("fox"));
    }
    EXPECT_EQUAL(4u, s.getNumSummaryFields());
    {
        EXPECT_EQUAL("foo", s.getSummaryField(0).getName());
        EXPECT_EQUAL(DataType::STRING, s.getSummaryField(0).getDataType());
        EXPECT_EQUAL(CollectionType::ARRAY, s.getSummaryField(0).getCollectionType());

        EXPECT_EQUAL("bar", s.getSummaryField(1).getName());
        EXPECT_EQUAL(DataType::INT32, s.getSummaryField(1).getDataType());
        EXPECT_EQUAL(CollectionType::WEIGHTEDSET, s.getSummaryField(1).getCollectionType());

        EXPECT_EQUAL("cox", s.getSummaryField(2).getName());
        EXPECT_EQUAL(DataType::STRING, s.getSummaryField(2).getDataType());
        EXPECT_EQUAL(CollectionType::SINGLE, s.getSummaryField(2).getCollectionType());

        EXPECT_EQUAL("fox", s.getSummaryField(3).getName());
        EXPECT_EQUAL(DataType::RAW, s.getSummaryField(3).getDataType());
        EXPECT_EQUAL(CollectionType::SINGLE, s.getSummaryField(3).getCollectionType());

        EXPECT_EQUAL(0u, s.getSummaryFieldId("foo"));
        EXPECT_EQUAL(1u, s.getSummaryFieldId("bar"));
        EXPECT_EQUAL(2u, s.getSummaryFieldId("cox"));
        EXPECT_EQUAL(3u, s.getSummaryFieldId("fox"));
        EXPECT_EQUAL(Schema::UNKNOWN_FIELD_ID, s.getSummaryFieldId("not"));
    }
    EXPECT_EQUAL(1u, s.getNumFieldSets());
    {
        EXPECT_EQUAL("default", s.getFieldSet(0).getName());
        EXPECT_EQUAL(2u, s.getFieldSet(0).getFields().size());
        EXPECT_EQUAL("foo", s.getFieldSet(0).getFields()[0]);
        EXPECT_EQUAL("bar", s.getFieldSet(0).getFields()[1]);
    }
    EXPECT_EQUAL(1u, s.getNumImportedAttributeFields());
    {
        const auto &imported = s.getImportedAttributeFields();
        EXPECT_EQUAL(1u, imported.size());
        TEST_DO(assertField(SIAF("imported", DataType::INT32, CollectionType::SINGLE), imported[0]));
    }
}

TEST("testLoadAndSave") {
    using SIF = Schema::IndexField;
    using SAF = Schema::AttributeField;
    using SSF = Schema::SummaryField;
    using SDT = schema::DataType;
    using SCT = schema::CollectionType;
    typedef Schema::FieldSet SFS;

    { // load from config -> save to file -> load from file
        Schema s;
        SchemaConfigurer configurer(s, "dir:" + TEST_PATH("load-save-cfg"));
        EXPECT_EQUAL(3u, s.getNumIndexFields());
        assertIndexField(SIF("a", SDT::STRING), s.getIndexField(0));
        assertIndexField(SIF("b", SDT::INT64), s.getIndexField(1));
        assertIndexField(SIF("c", SDT::STRING), s.getIndexField(2));

        EXPECT_EQUAL(9u, s.getNumAttributeFields());
        assertField(SAF("a", SDT::STRING, SCT::SINGLE),
                    s.getAttributeField(0));
        assertField(SAF("b", SDT::INT8, SCT::ARRAY), s.getAttributeField(1));
        assertField(SAF("c", SDT::INT16, SCT::WEIGHTEDSET),
                    s.getAttributeField(2));
        assertField(SAF("d", SDT::INT32),       s.getAttributeField(3));
        assertField(SAF("e", SDT::INT64),       s.getAttributeField(4));
        assertField(SAF("f", SDT::FLOAT),       s.getAttributeField(5));
        assertField(SAF("g", SDT::DOUBLE),      s.getAttributeField(6));
        assertField(SAF("h", SDT::BOOLEANTREE), s.getAttributeField(7));
        assertField(SAF("i", SDT::TENSOR), s.getAttributeField(8));

        EXPECT_EQUAL(12u, s.getNumSummaryFields());
        assertField(SSF("a", SDT::INT8),   s.getSummaryField(0));
        assertField(SSF("b", SDT::INT16),  s.getSummaryField(1));
        assertField(SSF("c", SDT::INT32),  s.getSummaryField(2));
        assertField(SSF("d", SDT::INT64),  s.getSummaryField(3));
        assertField(SSF("e", SDT::FLOAT),  s.getSummaryField(4));
        assertField(SSF("f", SDT::DOUBLE), s.getSummaryField(5));
        assertField(SSF("g", SDT::STRING), s.getSummaryField(6));
        assertField(SSF("h", SDT::STRING), s.getSummaryField(7));
        assertField(SSF("i", SDT::STRING), s.getSummaryField(8));
        assertField(SSF("j", SDT::STRING), s.getSummaryField(9));
        assertField(SSF("k", SDT::RAW),    s.getSummaryField(10));
        assertField(SSF("l", SDT::RAW),    s.getSummaryField(11));

        EXPECT_EQUAL(1u, s.getNumFieldSets());
        assertSet(SFS("default").addField("a").addField("c"),
                  s.getFieldSet(0));

        Schema s2 = s;
        EXPECT_TRUE(s.saveToFile("schema.txt"));
        assertSchema(s, s2); // test copy contructor
        Schema s3;
        EXPECT_TRUE(s3.loadFromFile("schema.txt"));
        assertSchema(s, s3); // test that saved file is loaded correctly
        s3.addIndexField(SIF("foo", SDT::STRING));
        s3.addImportedAttributeField(SIAF("imported", DataType::INT32));
        EXPECT_TRUE(s3.loadFromFile("schema.txt")); // load should clear the current content
        assertSchema(s, s3);
    }
    { // empty schema
        Schema s;
        EXPECT_TRUE(s.saveToFile("schema2.txt"));
        Schema s2;
        s2.addIndexField(SIF("foo", SDT::STRING));
        s2.addImportedAttributeField(SIAF("imported", DataType::INT32));
        EXPECT_TRUE(s2.loadFromFile("schema2.txt"));
        assertSchema(s, s2);
    }
    { // load with error
        Schema s;
        EXPECT_TRUE(!s.loadFromFile("not.txt"));
        EXPECT_TRUE(!s.saveToFile("not/not.txt"));
    }
}

TEST("require that schema can save and load timestamps for fields") {
    const fastos::TimeStamp timestamp(42);
    const std::string file_name = "schema-with-timestamps.txt";
    Schema s;
    Schema::IndexField f("foo", DataType::STRING);
    f.setTimestamp(timestamp);
    s.addIndexField(f);
    ASSERT_TRUE(s.saveToFile(file_name));
    Schema s2;
    ASSERT_TRUE(s2.loadFromFile(file_name));
    ASSERT_EQUAL(1u, s2.getNumIndexFields());
    ASSERT_EQUAL(timestamp, s2.getIndexField(0).getTimestamp());
}

TEST("require that timestamps are omitted when 0.") {
    const std::string file_name = "schema-without-timestamps.txt";
    Schema s;
    s.addIndexField(Schema::IndexField("foo", DataType::STRING));
    ASSERT_TRUE(s.saveToFile(file_name));

    std::ifstream file(file_name.c_str());
    ASSERT_TRUE(file.good());
    while (file) {
        std::string line;
        getline(file, line);
        EXPECT_NOT_EQUAL("indexfield[0].timestamp 0", line);
    }

    Schema s2;
    ASSERT_TRUE(s2.loadFromFile(file_name));
    ASSERT_EQUAL(1u, s2.getNumIndexFields());
}

void addAllFieldTypes(const string &name, Schema &schema,
                      fastos::TimeStamp timestamp) {
    Schema::IndexField index_field(name, DataType::STRING);
    index_field.setTimestamp(timestamp);
    schema.addIndexField(index_field);

    Schema::AttributeField attribute_field(name, DataType::STRING);
    attribute_field.setTimestamp(timestamp);
    schema.addAttributeField(attribute_field);

    Schema::SummaryField summary_field(name, DataType::STRING);
    summary_field.setTimestamp(timestamp);
    schema.addSummaryField(summary_field);

    schema.addFieldSet(Schema::FieldSet(name));
}

TEST("require that schemas can be added") {
    const string name1 = "foo";
    const string name2 = "bar";
    const fastos::TimeStamp timestamp1(42);
    const fastos::TimeStamp timestamp2(84);
    Schema s1;
    addAllFieldTypes(name1, s1, timestamp1);
    Schema s2;
    addAllFieldTypes(name2, s2, timestamp2);

    Schema::UP sum = Schema::make_union(s1, s2);
    ASSERT_EQUAL(2u, sum->getNumIndexFields());
    EXPECT_TRUE(s1.getIndexField(0) ==
                sum->getIndexField(sum->getIndexFieldId(name1)));
    EXPECT_TRUE(s2.getIndexField(0) ==
                sum->getIndexField(sum->getIndexFieldId(name2)));
    ASSERT_EQUAL(2u, sum->getNumAttributeFields());
    EXPECT_TRUE(s1.getAttributeField(0) ==
                sum->getAttributeField(sum->getAttributeFieldId(name1)));
    EXPECT_TRUE(s2.getAttributeField(0) ==
                sum->getAttributeField(sum->getAttributeFieldId(name2)));
    ASSERT_EQUAL(2u, sum->getNumSummaryFields());
    EXPECT_TRUE(s1.getSummaryField(0) ==
                sum->getSummaryField(sum->getSummaryFieldId(name1)));
    EXPECT_TRUE(s2.getSummaryField(0) ==
                sum->getSummaryField(sum->getSummaryFieldId(name2)));
    ASSERT_EQUAL(2u, sum->getNumFieldSets());
    EXPECT_TRUE(s1.getFieldSet(0) ==
                sum->getFieldSet(sum->getFieldSetId(name1)));
    EXPECT_TRUE(s2.getFieldSet(0) ==
                sum->getFieldSet(sum->getFieldSetId(name2)));
}

TEST("require that S union S = S for schema S") {
    Schema schema;
    addAllFieldTypes("foo", schema, 42);

    Schema::UP sum = Schema::make_union(schema, schema);
    EXPECT_TRUE(schema == *sum);
}

TEST("require that schema can calculate set_difference") {
    const string name1 = "foo";
    const string name2 = "bar";
    const fastos::TimeStamp timestamp1(42);
    const fastos::TimeStamp timestamp2(84);
    Schema s1;
    addAllFieldTypes(name1, s1, timestamp1);
    addAllFieldTypes(name2, s1, timestamp2);
    Schema s2;
    addAllFieldTypes(name2, s2, timestamp2);

    Schema::UP schema = Schema::set_difference(s1, s2);

    Schema expected;
    addAllFieldTypes(name1, expected, timestamp1);
    EXPECT_TRUE(expected == *schema);
}

TEST("require that getOldFields returns a subset of a schema") {
    Schema schema;
    const int64_t limit_timestamp = 1000;

    addAllFieldTypes("bar", schema, fastos::TimeStamp(limit_timestamp - 1));
    addAllFieldTypes("foo", schema, fastos::TimeStamp(limit_timestamp + 1));

    Schema::UP old_fields =
        schema.getOldFields(fastos::TimeStamp(limit_timestamp));

    EXPECT_EQUAL(1u, old_fields->getNumIndexFields());
    EXPECT_EQUAL("bar", old_fields->getIndexField(0).getName());
    EXPECT_EQUAL(1u, old_fields->getNumAttributeFields());
    EXPECT_EQUAL(1u, old_fields->getNumSummaryFields());
}

TEST("require that schema can calculate intersection") {
    const string name1 = "foo";
    const string name2 = "bar";
    const string name3 = "baz";
    const fastos::TimeStamp timestamp1(42);
    const fastos::TimeStamp timestamp2(84);
    Schema s1;
    addAllFieldTypes(name1, s1, timestamp1);
    addAllFieldTypes(name2, s1, timestamp2);
    Schema s2;
    addAllFieldTypes(name2, s2, timestamp2);
    addAllFieldTypes(name3, s2, timestamp2);

    Schema::UP schema = Schema::intersect(s1, s2);

    Schema expected;
    addAllFieldTypes(name2, expected, timestamp2);
    EXPECT_TRUE(expected == *schema);
}

TEST("require that incompatible fields are removed from intersection") {
    const string name = "foo";
    Schema s1;
    s1.addIndexField(Schema::IndexField(name, DataType::STRING));
    Schema s2;
    s2.addIndexField(Schema::IndexField(name, DataType::INT32));
    Schema::UP schema = Schema::intersect(s1, s2);
    EXPECT_EQUAL(0u, schema->getNumIndexFields());
    EXPECT_FALSE(schema->isIndexField(name));
}

TEST("require that imported attribute fields are not saved to disk")
{
    const vespalib::string fileName = "schema-no-imported-fields.txt";
    {
        Schema s;
        s.addImportedAttributeField(Schema::ImportedAttributeField("imported", DataType::INT32));
        s.saveToFile(fileName);
    }
    {
        Schema s;
        s.loadFromFile(fileName);
        EXPECT_EQUAL(0u, s.getNumImportedAttributeFields());
    }
}

TEST("require that schema can be built with imported attribute fields")
{
    Schema s;
    SchemaConfigurer configurer(s, "dir:" + TEST_PATH("imported-fields-cfg"));

    const auto &imported = s.getImportedAttributeFields();
    EXPECT_EQUAL(2u, imported.size());
    TEST_DO(assertField(SIAF("imported_a", DataType::INT32, CollectionType::SINGLE), imported[0]));
    TEST_DO(assertField(SIAF("imported_b", DataType::STRING, CollectionType::ARRAY), imported[1]));

    const auto &regular = s.getAttributeFields();
    EXPECT_EQUAL(1u, regular.size());
    TEST_DO(assertField(SIAF("regular", DataType::INT32, CollectionType::SINGLE), regular[0]));
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
