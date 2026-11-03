// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchsummary/docsummary/attribute_combiner_dfw.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer_state.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/struct_fields_mapper.h>
#include <vespa/searchsummary/docsummary/summary_elements_selector.h>
#include <vespa/searchsummary/test/mock_attribute_manager.h>
#include <vespa/searchsummary/test/mock_state_callback.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_combiner_test");

using search::MatchingElementsFields;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::getUndefined;
using search::docsummary::AttributeCombinerDFW;
using search::docsummary::CombinerShape;
using search::docsummary::DocsumFieldWriter;
using search::docsummary::GetDocsumsState;
using search::docsummary::GetDocsumsStateCallback;
using search::docsummary::IDocsumEnvironment;
using search::docsummary::StructFieldsMapper;
using search::docsummary::SummaryElementsSelector;
using search::docsummary::test::MockAttributeManager;
using search::docsummary::test::MockStateCallback;
using search::docsummary::test::SlimeValue;

namespace {

struct AttributeCombinerTest : public ::testing::Test {
    MockAttributeManager                     attrs;
    std::unique_ptr<DocsumFieldWriter>       writer;
    std::shared_ptr<MatchingElementsFields>  matching_elements_fields;
    MockStateCallback                        callback;
    GetDocsumsState                          state;
    StructFieldsMapper                       mapper;
    std::unique_ptr<SummaryElementsSelector> elements_selector;

    AttributeCombinerTest();
    ~AttributeCombinerTest() override;
    void set_field(const std::string& field_name, bool filter_elements,
                   const std::vector<std::string>& struct_fields = {},
                   CombinerShape                   declared_shape = CombinerShape::INFER);
    std::unique_ptr<DocsumFieldWriter> try_create(const std::string&              field_name,
                                                  const std::vector<std::string>& struct_fields = {},
                                                  CombinerShape declared_shape = CombinerShape::INFER) const {
        return AttributeCombinerDFW::create(field_name, *state._attrCtx, struct_fields, declared_shape);
    }
    void assertWritten(const std::string& exp, uint32_t docId);
    bool has_field(const std::string& field_name) const { return matching_elements_fields->has_field(field_name); }
    const std::string& enclosing_field(const std::string& field_name) const {
        return matching_elements_fields->enclosing_field(field_name);
    }
};

AttributeCombinerTest::AttributeCombinerTest()
    : attrs(),
      writer(),
      matching_elements_fields(std::make_shared<MatchingElementsFields>()),
      callback(),
      state(callback),
      mapper(),
      elements_selector() {
    attrs.build_string_attribute("array.name", {{"n1.1", "n1.2"}, {"n2"}, {"n3.1", "n3.2"}, {"", "n4.2"}, {}});
    attrs.build_int_attribute("array.val", BasicType::Type::INT8,
                              {{10, 11}, {20, 21}, {30}, {getUndefined<int8_t>(), 41}, {}});
    attrs.build_float_attribute("array.fval",
                                {{110.0}, {120.0, 121.0}, {130.0, 131.0}, {getUndefined<double>(), 141.0}, {}});
    attrs.build_bool_attribute("array.flag", {{1, 0}, {1, 1}, {0, 1}, {0, 1}, {}});
    attrs.build_string_attribute("smap.key", {{"k1.1", "k1.2"}, {"k2"}, {"k3.1", "k3.2"}, {"", "k4.2"}, {}});
    attrs.build_string_attribute("smap.value.name", {{"n1.1", "n1.2"}, {"n2"}, {"n3.1", "n3.2"}, {"", "n4.2"}, {}});
    attrs.build_int_attribute("smap.value.val", BasicType::Type::INT8,
                              {{10, 11}, {20, 21}, {30}, {getUndefined<int8_t>(), 41}, {}});
    attrs.build_float_attribute("smap.value.fval",
                                {{110.0}, {120.0, 121.0}, {130.0, 131.0}, {getUndefined<double>(), 141.0}, {}});
    attrs.build_bool_attribute("smap.value.flag", {{1, 0}, {1, 1}, {0, 1}, {0, 1}, {}});
    attrs.build_string_attribute("map.key", {{"k1.1", "k1.2"}, {"k2"}, {"k3.1"}, {"", "k4.2"}, {}});
    attrs.build_string_attribute("map.value", {{"n1.1", "n1.2"}, {}, {"n3.1", "n3.2"}, {"", "n4.2"}, {}});
    // "mixed.tags" is not an array attribute, and can thus only be used if it is not selected.
    attrs.build_string_attribute("mixed.name", {{"n1.1", "n1.2"}, {"n2"}, {}, {}, {}});
    attrs.build_int_attribute("mixed.val", BasicType::Type::INT8, {{10, 11}, {20}, {}, {}, {}});
    attrs.build_string_attribute("mixed.tags", {{"t1"}, {"t2"}, {}, {}, {}}, CollectionType::WSET);
    // "pair" has exactly the two sub-fields which make a map of scalar indistinguishable from an array
    // of struct when the shape is deduced. The empty value of document 1 is what tells them apart.
    attrs.build_string_attribute("pair.key", {{"k1"}, {"k2"}, {}, {}, {}});
    attrs.build_string_attribute("pair.value", {{""}, {"v2"}, {}, {}, {}});

    callback.add_matching_elements(1, "array", {1});
    callback.add_matching_elements(3, "array", {0});
    callback.add_matching_elements(4, "array", {1});
    callback.add_matching_elements(1, "smap", {1});
    callback.add_matching_elements(3, "smap", {0});
    callback.add_matching_elements(4, "smap", {1});
    callback.add_matching_elements(1, "map", {1});
    callback.add_matching_elements(3, "map", {0});
    callback.add_matching_elements(4, "map", {1});

    state._attrCtx = attrs.mgr().createContext();
    state._matching_elements_fields = matching_elements_fields;
    mapper.setup(*state._attrCtx);
}

AttributeCombinerTest::~AttributeCombinerTest() = default;

void AttributeCombinerTest::set_field(const std::string& field_name, bool filter_elements,
                                      const std::vector<std::string>& struct_fields, CombinerShape declared_shape) {
    if (filter_elements) {
        elements_selector = std::make_unique<SummaryElementsSelector>(
            SummaryElementsSelector::select_by_match(field_name, mapper.get_struct_fields(field_name)));
    } else {
        elements_selector = std::make_unique<SummaryElementsSelector>(SummaryElementsSelector::select_all());
    }
    elements_selector->maybe_apply_to(*matching_elements_fields);
    writer = try_create(field_name, struct_fields, declared_shape);
    ASSERT_TRUE(writer);
    EXPECT_TRUE(writer->setFieldWriterStateIndex(0));
    // assign() rather than resize(), so that a second set_field() in the same test does not reuse the
    // writer state belonging to the previous writer.
    state._fieldWriterStates.assign(1, nullptr);
}

void AttributeCombinerTest::assertWritten(const std::string& exp_slime_as_json, uint32_t docId) {
    vespalib::Slime                act;
    vespalib::slime::SlimeInserter inserter(act);
    writer->insert_field(docId, nullptr, state, elements_selector->get_selected_elements(docId, state), inserter);

    SlimeValue exp(exp_slime_as_json);
    EXPECT_EQ(exp.slime, act);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_array_of_struct) {
    set_field("array", false);
    assertWritten("[ { flag: true, fval: 110.0, name: 'n1.1', val: 10}, { flag: false, name: 'n1.2', val: 11}]", 1);
    assertWritten("[ { flag: true, fval: 120.0, name: 'n2', val: 20}, { flag: true, fval: 121.0, val: 21 }]", 2);
    assertWritten("[ { flag: false, fval: 130.0, name: 'n3.1', val: 30}, { flag: true, fval: 131.0, name: 'n3.2'} ]",
                  3);
    assertWritten("[ { flag: false }, { flag: true, fval: 141.0, name: 'n4.2', val:  41} ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_map_of_struct) {
    set_field("smap", false);
    assertWritten("[ { key: 'k1.1', value: { flag: true, fval: 110.0, name: 'n1.1', val: 10} }, { key: 'k1.2', "
                  "value: { flag: false, name: 'n1.2', val: 11} }]",
                  1);
    assertWritten("[ { key: 'k2', value: { flag: true, fval: 120.0, name: 'n2', val: 20} }]", 2);
    assertWritten("[ { key: 'k3.1', value: { flag: false, fval: 130.0, name: 'n3.1', val: 30} }, { key: 'k3.2', "
                  "value: { flag: true, fval: 131.0, name: 'n3.2'} } ]",
                  3);
    assertWritten("[ { key: '', value: { flag: false } }, { key: 'k4.2', value: { flag: true, fval: 141.0, name: "
                  "'n4.2', val:  41} } ]",
                  4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_map_of_string) {
    set_field("map", false);
    assertWritten("[ { key: 'k1.1', value: 'n1.1' }, { key: 'k1.2', value: 'n1.2'}]", 1);
    assertWritten("[ { key: 'k2', value: '' }]", 2);
    assertWritten("[ { key: 'k3.1', value: 'n3.1' }, { key: '', value: 'n3.2'} ]", 3);
    assertWritten("[ { key: '', value: '' }, { key: 'k4.2', value: 'n4.2' } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_filtered_array_of_struct) {
    set_field("array", true);
    assertWritten("[ { flag: false, name: 'n1.2', val: 11}]", 1);
    assertWritten("null", 2);
    assertWritten("[ { flag: false, fval: 130.0, name: 'n3.1', val: 30} ]", 3);
    assertWritten("[ { flag: true, fval: 141.0, name: 'n4.2', val:  41} ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_filtered_map_of_struct) {
    set_field("smap", true);
    assertWritten("[ { key: 'k1.2', value: { flag: false, name: 'n1.2', val: 11} }]", 1);
    assertWritten("null", 2);
    assertWritten("[ { key: 'k3.1', value: { flag: false, fval: 130.0, name: 'n3.1', val: 30} } ]", 3);
    assertWritten("[ { key: 'k4.2', value: { flag: true, fval: 141.0, name: 'n4.2', val:  41} } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_filtered_map_of_string) {
    set_field("map", true);
    assertWritten("[ { key: 'k1.2', value: 'n1.2'}]", 1);
    assertWritten("null", 2);
    assertWritten("[ { key: 'k3.1', value: 'n3.1' } ]", 3);
    assertWritten("[ { key: 'k4.2', value: 'n4.2' } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_matching_elems_fields_is_setup_for_filtered_array_of_struct) {
    set_field("array", true);
    ASSERT_TRUE(elements_selector);
    EXPECT_TRUE(has_field("array"));
    EXPECT_TRUE(has_field("array.name"));
    EXPECT_TRUE(has_field("array.val"));
    EXPECT_FALSE(has_field("map"));
    EXPECT_FALSE(has_field("smap"));
    EXPECT_EQ("array.foo", enclosing_field("array.foo"));
    EXPECT_EQ("array", enclosing_field("array.name"));
    EXPECT_EQ("array", enclosing_field("array.val"));
    EXPECT_EQ("array", enclosing_field("array.fval"));
    EXPECT_EQ("array", enclosing_field("array.flag"));
}

TEST_F(AttributeCombinerTest, require_that_matching_elems_fields_is_setup_for_filtered_map_of_struct) {
    set_field("smap", true);
    EXPECT_TRUE(elements_selector);
    EXPECT_FALSE(has_field("array"));
    EXPECT_FALSE(has_field("map"));
    EXPECT_TRUE(has_field("smap"));
    EXPECT_TRUE(has_field("smap.key"));
    EXPECT_EQ("smap.foo", enclosing_field("smap.foo"));
    EXPECT_EQ("smap", enclosing_field("smap.key"));
    EXPECT_EQ("smap", enclosing_field("smap.value.name"));
    EXPECT_EQ("smap", enclosing_field("smap.value.val"));
    EXPECT_EQ("smap", enclosing_field("smap.value.fval"));
    EXPECT_EQ("smap", enclosing_field("smap.value.flag"));
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_selected_struct_fields_of_array) {
    set_field("array", false, {"name", "val"});
    assertWritten("[ { name: 'n1.1', val: 10}, { name: 'n1.2', val: 11}]", 1);
    assertWritten("[ { name: 'n2', val: 20}, { val: 21 }]", 2);
    assertWritten("[ { name: 'n3.1', val: 30}, { name: 'n3.2'} ]", 3);
    assertWritten("[ { }, { name: 'n4.2', val:  41} ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_selecting_a_subset_does_not_change_the_number_of_elements) {
    // "array.fval" has fewer values than the other sub-fields for documents 1 and 4, but the array still
    // has as many elements as it has without a selection.
    set_field("array", false, {"fval"});
    assertWritten("[ { fval: 110.0 }, { } ]", 1);
    assertWritten("[ { fval: 120.0 }, { fval: 121.0 } ]", 2);
    assertWritten("[ { fval: 130.0 }, { fval: 131.0 } ]", 3);
    assertWritten("[ { }, { fval: 141.0 } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_matching_elements_are_kept_when_a_subset_is_selected) {
    // The matching element of document 1 is element 1, which "array.fval" itself has no value for. The
    // element is still there, since the unselected sub-fields determine the number of elements.
    set_field("array", true, {"fval"});
    assertWritten("[ { } ]", 1);
    assertWritten("null", 2);
    assertWritten("[ { fval: 130.0 } ]", 3);
    assertWritten("[ { fval: 141.0 } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_the_key_alone_determines_the_number_of_elements_of_a_map_of_struct) {
    // "smap.key" has only one value for document 2, while "smap.value.val", "smap.value.fval" and
    // "smap.value.flag" have two. A map entry exists only if it has a key, so the map has one element
    // whether or not those longer sub-fields are selected.
    set_field("smap", false);
    assertWritten("[ { key: 'k2', value: { flag: true, fval: 120.0, name: 'n2', val: 20} }]", 2);
    set_field("smap", false, {"value.val"});
    assertWritten("[ { key: 'k2', value: { val: 20 } }]", 2);
    set_field("smap", false, {"value.name"});
    assertWritten("[ { key: 'k2', value: { name: 'n2' } }]", 2);
}

TEST_F(AttributeCombinerTest, require_that_matching_elements_are_kept_when_a_subset_of_a_map_is_selected) {
    // The matching element of document 1 is element 1, which "smap.value.fval" itself has no value for.
    // The element is still there, since it is the key which determines the number of elements.
    set_field("smap", true, {"value.fval"});
    assertWritten("[ { key: 'k1.2', value: { } } ]", 1);
    assertWritten("null", 2);
    assertWritten("[ { key: 'k3.1', value: { fval: 130.0 } } ]", 3);
    assertWritten("[ { key: 'k4.2', value: { fval: 141.0 } } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_a_declared_shape_selects_the_writer_it_names) {
    set_field("array", false, {}, CombinerShape::ARRAY_OF_STRUCT);
    assertWritten("[ { flag: true, fval: 110.0, name: 'n1.1', val: 10}, { flag: false, name: 'n1.2', val: 11}]", 1);
    set_field("smap", false, {}, CombinerShape::MAP_OF_STRUCT);
    assertWritten("[ { key: 'k1.1', value: { flag: true, fval: 110.0, name: 'n1.1', val: 10} }, { key: 'k1.2', "
                  "value: { flag: false, name: 'n1.2', val: 11} }]",
                  1);
    set_field("map", false, {}, CombinerShape::MAP_OF_SCALAR);
    assertWritten("[ { key: 'k1.1', value: 'n1.1' }, { key: 'k1.2', value: 'n1.2'}]", 1);
}

TEST_F(AttributeCombinerTest, require_that_a_declared_shape_which_the_attributes_do_not_match_is_ignored) {
    // Reported and then resolved as if no shape had been declared. Config and attributes disagreeing can
    // only come from a config model which disagrees with this backend, and failing the field would cost
    // the whole node its summary config.
    set_field("array", false, {}, CombinerShape::MAP_OF_STRUCT);
    assertWritten("[ { flag: true, fval: 110.0, name: 'n1.1', val: 10}, { flag: false, name: 'n1.2', val: 11}]", 1);
    set_field("array", false, {}, CombinerShape::MAP_OF_SCALAR);
    assertWritten("[ { flag: true, fval: 110.0, name: 'n1.1', val: 10}, { flag: false, name: 'n1.2', val: 11}]", 1);
    set_field("smap", false, {}, CombinerShape::ARRAY_OF_STRUCT);
    assertWritten("[ { key: 'k1.1', value: { flag: true, fval: 110.0, name: 'n1.1', val: 10} }, { key: 'k1.2', "
                  "value: { flag: false, name: 'n1.2', val: 11} }]",
                  1);
}

TEST_F(AttributeCombinerTest, require_that_a_declared_shape_decides_whether_an_empty_value_is_kept) {
    // As a map of scalar the empty value is kept, so that the key/value pair stays complete. As an array
    // of struct an empty string is simply absent, like any other sub-field without a value.
    set_field("pair", false, {}, CombinerShape::MAP_OF_SCALAR);
    assertWritten("[ { key: 'k1', value: '' } ]", 1);
    set_field("pair", false, {}, CombinerShape::ARRAY_OF_STRUCT);
    assertWritten("[ { key: 'k1' } ]", 1);
    // Deduced, "pair" is taken for a map of scalar, which is what declaring the shape is there to fix.
    set_field("pair", false);
    assertWritten("[ { key: 'k1', value: '' } ]", 1);
}

TEST_F(AttributeCombinerTest, require_that_a_struct_field_selection_is_validated_against_the_resolved_shape) {
    // "value.name" is only selectable for a map of struct. Declaring "array" as one does not make it one:
    // the declared shape is checked, so the selection is still validated against an array of struct.
    EXPECT_FALSE(try_create("array", {"value.name"}, CombinerShape::MAP_OF_STRUCT));
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_is_not_created_when_a_struct_field_is_not_an_array) {
    EXPECT_FALSE(try_create("mixed"));
}

TEST_F(AttributeCombinerTest, require_that_a_selected_struct_field_that_is_not_an_array_is_still_an_error) {
    EXPECT_FALSE(try_create("mixed", {"name", "tags"}));
}

TEST_F(AttributeCombinerTest, require_that_selecting_a_struct_field_which_does_not_exist_is_an_error) {
    EXPECT_FALSE(try_create("array", {"name", "nosuchfield"}));
}

TEST_F(AttributeCombinerTest, require_that_selecting_a_value_sub_field_which_does_not_exist_in_a_map_is_an_error) {
    EXPECT_FALSE(try_create("smap", {"value.nosuchfield"}));
}

TEST_F(AttributeCombinerTest, require_that_selecting_a_bare_value_of_a_map_of_struct_is_an_error) {
    EXPECT_FALSE(try_create("smap", {"value"}));
}

TEST_F(AttributeCombinerTest, require_that_selecting_no_value_sub_fields_of_a_map_of_struct_is_an_error) {
    EXPECT_FALSE(try_create("smap", {"key"}));
}

TEST_F(AttributeCombinerTest,
       require_that_the_key_of_a_map_of_struct_may_be_selected_along_with_its_value_sub_fields) {
    set_field("smap", false, {"key", "value.name"});
    assertWritten("[ { key: 'k1.1', value: { name: 'n1.1'} }, { key: 'k1.2', value: { name: 'n1.2'} }]", 1);
    assertWritten("[ { key: 'k2', value: { name: 'n2'} }]", 2);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_struct_fields_that_are_not_arrays_are_ignored_when_they_are_not_selected) {
    set_field("mixed", false, {"name", "val"});
    assertWritten("[ { name: 'n1.1', val: 10}, { name: 'n1.2', val: 11}]", 1);
    assertWritten("[ { name: 'n2', val: 20}]", 2);
    assertWritten("null", 3);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_selected_key_of_map_of_string) {
    set_field("map", false, {"key"});
    assertWritten("[ { key: 'k1.1' }, { key: 'k1.2' }]", 1);
    assertWritten("[ { key: 'k2' }]", 2);
    assertWritten("[ { key: 'k3.1' }, { key: '' } ]", 3);
    assertWritten("[ { key: '' }, { key: 'k4.2' } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_selected_value_of_map_of_string) {
    set_field("map", false, {"value"});
    assertWritten("[ { value: 'n1.1' }, { value: 'n1.2' }]", 1);
    assertWritten("[ { value: '' }]", 2);
    assertWritten("[ { value: 'n3.1' }, { value: 'n3.2' } ]", 3);
    assertWritten("[ { value: '' }, { value: 'n4.2' } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_selected_struct_fields_of_map) {
    set_field("smap", false, {"value.name"});
    assertWritten("[ { key: 'k1.1', value: { name: 'n1.1'} }, { key: 'k1.2', value: { name: 'n1.2'} }]", 1);
    assertWritten("[ { key: 'k2', value: { name: 'n2'} }]", 2);
    assertWritten("[ { key: 'k3.1', value: { name: 'n3.1'} }, { key: 'k3.2', value: { name: 'n3.2'} } ]", 3);
    assertWritten("[ { key: '', value: { } }, { key: 'k4.2', value: { name: 'n4.2'} } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest,
       require_that_attribute_combiner_dfw_generates_correct_slime_output_for_several_selected_struct_fields_of_map) {
    set_field("smap", false, {"value.name", "value.val"});
    assertWritten("[ { key: 'k1.1', value: { name: 'n1.1', val: 10} }, { key: 'k1.2', value: { name: 'n1.2', "
                  "val: 11} }]",
                  1);
    assertWritten("[ { key: 'k2', value: { name: 'n2', val: 20} }]", 2);
    assertWritten("[ { key: 'k3.1', value: { name: 'n3.1', val: 30} }, { key: 'k3.2', value: { name: 'n3.2'} } ]", 3);
    assertWritten("[ { key: '', value: { } }, { key: 'k4.2', value: { name: 'n4.2', val:  41} } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_matching_elems_fields_is_setup_for_filtered_map_of_string) {
    set_field("map", true);
    EXPECT_TRUE(elements_selector);
    EXPECT_FALSE(has_field("array"));
    EXPECT_TRUE(has_field("map"));
    EXPECT_TRUE(has_field("map.key"));
    EXPECT_TRUE(has_field("map.value"));
    EXPECT_FALSE(has_field("smap"));
    EXPECT_EQ("map.foo", enclosing_field("map.foo"));
    EXPECT_EQ("map", enclosing_field("map.key"));
    EXPECT_EQ("map", enclosing_field("map.value"));
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
