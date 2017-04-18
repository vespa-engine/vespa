// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for schemautil.

#include <vespa/fastos/fastos.h>
#include <vespa/searchcore/proton/common/schemautil.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace proton;
using namespace search::index;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using vespalib::string;


namespace {

void addAllFieldTypes(const string &name, Schema &schema,
                      fastos::TimeStamp timestamp)
{
    Schema::IndexField index_field(name, DataType::STRING);
    index_field.setTimestamp(timestamp);
    schema.addIndexField(index_field);

    Schema::AttributeField attribute_field(name, DataType::STRING);
    attribute_field.setTimestamp(timestamp);
    schema.addAttributeField(attribute_field);

    Schema::SummaryField summary_field(name, DataType::STRING);
    summary_field.setTimestamp(timestamp);
    schema.addSummaryField(summary_field);
}

TEST("require that makeHistorySchema sets timestamp")
{
    Schema old_schema;
    Schema new_schema;
    Schema old_history;

    const fastos::TimeStamp now(84);
    const string name = "foo";
    addAllFieldTypes(name, old_schema, fastos::TimeStamp(0));

    Schema::SP schema = SchemaUtil::makeHistorySchema(new_schema, old_schema,
                                                      old_history, now);

    ASSERT_EQUAL(1u, schema->getNumIndexFields());
    EXPECT_EQUAL(name, schema->getIndexField(0).getName());
    EXPECT_EQUAL(now, schema->getIndexField(0).getTimestamp());

    ASSERT_EQUAL(1u, schema->getNumAttributeFields());
    EXPECT_EQUAL(name, schema->getAttributeField(0).getName());
    EXPECT_EQUAL(now, schema->getAttributeField(0).getTimestamp());

    ASSERT_EQUAL(1u, schema->getNumSummaryFields());
    EXPECT_EQUAL(name, schema->getSummaryField(0).getName());
    EXPECT_EQUAL(now, schema->getSummaryField(0).getTimestamp());
}

TEST("require that makeHistorySchema preserves timestamp")
{
    Schema old_schema;
    Schema new_schema;
    Schema old_history;

    const fastos::TimeStamp timestamp(42);
    const string name = "foo";
    addAllFieldTypes("bar", old_schema, fastos::TimeStamp(0));
    addAllFieldTypes(name, old_history, timestamp);

    Schema::SP schema =
        SchemaUtil::makeHistorySchema(new_schema, old_schema, old_history);

    ASSERT_EQUAL(2u, schema->getNumIndexFields());
    uint32_t id = schema->getIndexFieldId(name);
    ASSERT_NOT_EQUAL(id, Schema::UNKNOWN_FIELD_ID);
    EXPECT_EQUAL(timestamp, schema->getIndexField(id).getTimestamp());

    ASSERT_EQUAL(2u, schema->getNumAttributeFields());
    id = schema->getAttributeFieldId(name);
    ASSERT_NOT_EQUAL(id, Schema::UNKNOWN_FIELD_ID);
    EXPECT_EQUAL(timestamp, schema->getAttributeField(id).getTimestamp());

    ASSERT_EQUAL(2u, schema->getNumSummaryFields());
    id = schema->getSummaryFieldId(name);
    ASSERT_NOT_EQUAL(id, Schema::UNKNOWN_FIELD_ID);
    EXPECT_EQUAL(timestamp, schema->getSummaryField(id).getTimestamp());
}

struct ListSchemaResult {
    std::vector<vespalib::string> fieldNames;
    std::vector<vespalib::string> fieldDataTypes;
    std::vector<vespalib::string> fieldCollectionTypes;
    std::vector<vespalib::string> fieldLocations;
};

void
assertSchemaResult(const vespalib::string &name,
                   const vespalib::string &dataType,
                   const vespalib::string &collectionType,
                   const vespalib::string &location,
                   const ListSchemaResult &r,
                   size_t i)
{
    EXPECT_EQUAL(name, r.fieldNames[i]);
    EXPECT_EQUAL(dataType, r.fieldDataTypes[i]);
    EXPECT_EQUAL(collectionType, r.fieldCollectionTypes[i]);
    EXPECT_EQUAL(location, r.fieldLocations[i]);
}

TEST("require that listSchema can list all fields")
{
    Schema schema;
    schema.addIndexField(Schema::IndexField("if", DataType::STRING));
    schema.addAttributeField(Schema::AttributeField("af", DataType::INT32));
    schema.addSummaryField(Schema::SummaryField("sf", DataType::FLOAT, CollectionType::ARRAY));

    ListSchemaResult r;
    SchemaUtil::listSchema(schema, r.fieldNames, r.fieldDataTypes, r.fieldCollectionTypes, r.fieldLocations);
    EXPECT_EQUAL(3u, r.fieldNames.size());
    EXPECT_EQUAL(3u, r.fieldDataTypes.size());
    EXPECT_EQUAL(3u, r.fieldCollectionTypes.size());
    EXPECT_EQUAL(3u, r.fieldLocations.size());
    assertSchemaResult("af", "INT32", "SINGLE", "a", r, 0);
    assertSchemaResult("if", "STRING", "SINGLE", "i", r, 1);
    assertSchemaResult("sf", "FLOAT", "ARRAY", "s", r, 2);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
