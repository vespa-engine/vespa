// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchsummary/docsummary/struct_fields_mapper.h>
#include <vespa/searchsummary/docsummary/summary_elements_selector.h>
#include <vespa/searchsummary/test/mock_attribute_manager.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::MatchingElementsFields;
using search::docsummary::StructFieldsMapper;
using search::docsummary::SummaryElementsSelector;
using search::docsummary::test::MockAttributeManager;
using search::index::Schema;
using SIF = Schema::IndexField;
using search::index::schema::CollectionType;
using search::index::schema::DataType;

using StringVector = std::vector<std::string>;

namespace {

Schema make_schema() {
    Schema s;
    s.addUriIndexFields(SIF("url", DataType::STRING));
    s.addIndexField(SIF("title", DataType::STRING));
    s.addIndexField(SIF("persons.name", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(SIF("persons.title", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(SIF("my_map.value", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(SIF("people.key", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(SIF("people.value.name", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(SIF("people.value.title", DataType::STRING, CollectionType::ARRAY));
    return s;
}

} // namespace

TEST(StructFieldsMapperTest, struct_field_indexes_are_mapped_from_schema) {
    StructFieldsMapper mapper;
    mapper.setup(make_schema());
    EXPECT_EQ((StringVector{"persons.name", "persons.title"}), mapper.get_struct_fields("persons"));
    EXPECT_EQ((StringVector{"my_map.value"}), mapper.get_struct_fields("my_map"));
    // uri parts are not struct fields
    EXPECT_EQ(StringVector(), mapper.get_struct_fields("url"));
    EXPECT_EQ(StringVector(), mapper.get_struct_fields("title"));
}

TEST(StructFieldsMapperTest, struct_field_attributes_and_indexes_are_merged) {
    MockAttributeManager attrs;
    attrs.build_int_attribute("persons.age", search::attribute::BasicType::INT32, {{1}});
    attrs.build_string_attribute("persons.name", {{"a"}});
    attrs.build_string_attribute("my_map.key", {{"k"}});
    StructFieldsMapper mapper;
    mapper.setup(*attrs.mgr().createContext());
    mapper.setup(make_schema());
    EXPECT_EQ((StringVector{"persons.age", "persons.name", "persons.title"}), mapper.get_struct_fields("persons"));
    EXPECT_EQ((StringVector{"my_map.key", "my_map.value"}), mapper.get_struct_fields("my_map"));
}

TEST(StructFieldsMapperTest, matched_elements_only_maps_struct_field_indexes_to_enclosing_field) {
    StructFieldsMapper mapper;
    mapper.setup(make_schema());
    MatchingElementsFields fields;
    SummaryElementsSelector::select_by_match("persons", mapper.get_struct_fields("persons")).apply_to(fields);
    EXPECT_TRUE(fields.has_field("persons"));
    EXPECT_TRUE(fields.has_field("persons.name"));
    EXPECT_TRUE(fields.has_field("persons.title"));
    EXPECT_EQ("persons", fields.enclosing_field("persons.name"));
    EXPECT_EQ("persons", fields.enclosing_field("persons.title"));
    EXPECT_FALSE(fields.has_field("url.host"));
}

TEST(StructFieldsMapperTest, nested_map_of_struct_field_indexes_are_mapped_to_outermost_field) {
    StructFieldsMapper mapper;
    mapper.setup(make_schema());
    EXPECT_EQ((StringVector{"people.key", "people.value.name", "people.value.title"}),
              mapper.get_struct_fields("people"));
    EXPECT_EQ(StringVector(), mapper.get_struct_fields("people.value"));
    MatchingElementsFields fields;
    SummaryElementsSelector::select_by_match("people", mapper.get_struct_fields("people")).apply_to(fields);
    EXPECT_TRUE(fields.has_field("people.value.name"));
    EXPECT_EQ("people", fields.enclosing_field("people.key"));
    EXPECT_EQ("people", fields.enclosing_field("people.value.name"));
    EXPECT_EQ("people", fields.enclosing_field("people.value.title"));
}

GTEST_MAIN_RUN_ALL_TESTS()
