// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer_state.h>
#include <vespa/searchsummary/docsummary/attribute_combiner_dfw.h>
#include <vespa/searchsummary/test/mock_attribute_manager.h>
#include <vespa/searchsummary/test/mock_state_callback.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_combiner_test");

using search::attribute::BasicType;
using search::attribute::getUndefined;
using search::docsummary::AttributeCombinerDFW;
using search::docsummary::GetDocsumsState;
using search::docsummary::GetDocsumsStateCallback;
using search::docsummary::IDocsumEnvironment;
using search::docsummary::DocsumFieldWriter;
using search::docsummary::test::MockAttributeManager;
using search::docsummary::test::MockStateCallback;
using search::docsummary::test::SlimeValue;

namespace {

struct AttributeCombinerTest : public ::testing::Test
{
    MockAttributeManager                attrs;
    std::unique_ptr<DocsumFieldWriter>  writer;
    MockStateCallback                   callback;
    GetDocsumsState                     state;
    std::shared_ptr<search::MatchingElementsFields> _matching_elems_fields;

    AttributeCombinerTest();
    ~AttributeCombinerTest() override;
    void set_field(const vespalib::string &field_name, bool filter_elements);
    void assertWritten(const vespalib::string &exp, uint32_t docId);
};

AttributeCombinerTest::AttributeCombinerTest()
    : attrs(),
      writer(),
      callback(),
      state(callback),
      _matching_elems_fields()
{
    attrs.build_string_attribute("array.name", {{"n1.1", "n1.2"}, {"n2"}, {"n3.1", "n3.2"}, {"", "n4.2"}, {}});
    attrs.build_int_attribute("array.val", BasicType::Type::INT8, {{ 10, 11}, {20, 21 }, {30}, { getUndefined<int8_t>(), 41}, {}});
    attrs.build_float_attribute("array.fval", {{ 110.0}, { 120.0, 121.0 }, { 130.0, 131.0}, { getUndefined<double>(), 141.0 }, {}});
    attrs.build_string_attribute("smap.key", {{"k1.1", "k1.2"}, {"k2"}, {"k3.1", "k3.2"}, {"", "k4.2"}, {}});
    attrs.build_string_attribute("smap.value.name", {{"n1.1", "n1.2"}, {"n2"}, {"n3.1", "n3.2"}, {"", "n4.2"}, {}});
    attrs.build_int_attribute("smap.value.val", BasicType::Type::INT8, {{ 10, 11}, {20, 21 }, {30}, { getUndefined<int8_t>(), 41}, {}});
    attrs.build_float_attribute("smap.value.fval", {{ 110.0}, { 120.0, 121.0 }, { 130.0, 131.0}, { getUndefined<double>(), 141.0 }, {}});
    attrs.build_string_attribute("map.key", {{"k1.1", "k1.2"}, {"k2"}, {"k3.1"}, {"", "k4.2"}, {}});
    attrs.build_string_attribute("map.value", {{"n1.1", "n1.2"}, {}, {"n3.1", "n3.2"}, {"", "n4.2"}, {}});

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
}

AttributeCombinerTest::~AttributeCombinerTest() = default;

void
AttributeCombinerTest::set_field(const vespalib::string &field_name, bool filter_elements)
{
    if (filter_elements) {
        _matching_elems_fields = std::make_shared<search::MatchingElementsFields>();
    }
    writer = AttributeCombinerDFW::create(field_name, *state._attrCtx, filter_elements, _matching_elems_fields);
    EXPECT_TRUE(writer->setFieldWriterStateIndex(0));
    state._fieldWriterStates.resize(1);
}

void
AttributeCombinerTest::assertWritten(const vespalib::string &exp_slime_as_json, uint32_t docId)
{
    vespalib::Slime act;
    vespalib::slime::SlimeInserter inserter(act);
    writer->insertField(docId, nullptr, state, inserter);

    SlimeValue exp(exp_slime_as_json);
    EXPECT_EQ(exp.slime, act);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_array_of_struct)
{
    set_field("array", false);
    assertWritten("[ { fval: 110.0, name: 'n1.1', val: 10}, { name: 'n1.2', val: 11}]", 1);
    assertWritten("[ { fval: 120.0, name: 'n2', val: 20}, { fval: 121.0, val: 21 }]", 2);
    assertWritten("[ { fval: 130.0, name: 'n3.1', val: 30}, { fval: 131.0, name: 'n3.2'} ]", 3);
    assertWritten("[ { }, { fval: 141.0, name: 'n4.2', val:  41} ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_map_of_struct)
{
    set_field("smap", false);
    assertWritten("[ { key: 'k1.1', value: { fval: 110.0, name: 'n1.1', val: 10} }, { key: 'k1.2', value: { name: 'n1.2', val: 11} }]", 1);
    assertWritten("[ { key: 'k2', value: { fval: 120.0, name: 'n2', val: 20} }, { key: '', value: { fval: 121.0, val: 21 } }]", 2);
    assertWritten("[ { key: 'k3.1', value: { fval: 130.0, name: 'n3.1', val: 30} }, { key: 'k3.2', value: { fval: 131.0, name: 'n3.2'} } ]", 3);
    assertWritten("[ { key: '', value: { } }, { key: 'k4.2', value: { fval: 141.0, name: 'n4.2', val:  41} } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_map_of_string)
{
    set_field("map", false);
    assertWritten("[ { key: 'k1.1', value: 'n1.1' }, { key: 'k1.2', value: 'n1.2'}]", 1);
    assertWritten("[ { key: 'k2', value: '' }]", 2);
    assertWritten("[ { key: 'k3.1', value: 'n3.1' }, { key: '', value: 'n3.2'} ]", 3);
    assertWritten("[ { key: '', value: '' }, { key: 'k4.2', value: 'n4.2' } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_filtered_array_of_struct)
{
    set_field("array", true);
    assertWritten("[ { name: 'n1.2', val: 11}]", 1);
    assertWritten("null", 2);
    assertWritten("[ { fval: 130.0, name: 'n3.1', val: 30} ]", 3);
    assertWritten("[ { fval: 141.0, name: 'n4.2', val:  41} ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_filtered_map_of_struct)
{
    set_field("smap", true);
    assertWritten("[ { key: 'k1.2', value: { name: 'n1.2', val: 11} }]", 1);
    assertWritten("null", 2);
    assertWritten("[ { key: 'k3.1', value: { fval: 130.0, name: 'n3.1', val: 30} } ]", 3);
    assertWritten("[ { key: 'k4.2', value: { fval: 141.0, name: 'n4.2', val:  41} } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_attribute_combiner_dfw_generates_correct_slime_output_for_filtered_map_of_string)
{
    set_field("map", true);
    assertWritten("[ { key: 'k1.2', value: 'n1.2'}]", 1);
    assertWritten("null", 2);
    assertWritten("[ { key: 'k3.1', value: 'n3.1' } ]", 3);
    assertWritten("[ { key: 'k4.2', value: 'n4.2' } ]", 4);
    assertWritten("null", 5);
}

TEST_F(AttributeCombinerTest, require_that_matching_elems_fields_is_setup_for_filtered_array_of_struct)
{
    set_field("array", true);
    EXPECT_TRUE(_matching_elems_fields);
    EXPECT_TRUE(_matching_elems_fields->has_field("array"));
    EXPECT_FALSE(_matching_elems_fields->has_field("map"));
    EXPECT_FALSE(_matching_elems_fields->has_field("smap"));
    EXPECT_EQ("", _matching_elems_fields->get_enclosing_field("array.foo"));
    EXPECT_EQ("array", _matching_elems_fields->get_enclosing_field("array.name"));
    EXPECT_EQ("array", _matching_elems_fields->get_enclosing_field("array.val"));
    EXPECT_EQ("array", _matching_elems_fields->get_enclosing_field("array.fval"));
}

TEST_F(AttributeCombinerTest, require_that_matching_elems_fields_is_setup_for_filtered_map_of_struct)
{
    set_field("smap", true);
    EXPECT_TRUE(_matching_elems_fields);
    EXPECT_FALSE(_matching_elems_fields->has_field("array"));
    EXPECT_FALSE(_matching_elems_fields->has_field("map"));
    EXPECT_TRUE(_matching_elems_fields->has_field("smap"));
    EXPECT_EQ("", _matching_elems_fields->get_enclosing_field("smap.foo"));
    EXPECT_EQ("smap", _matching_elems_fields->get_enclosing_field("smap.key"));
    EXPECT_EQ("smap", _matching_elems_fields->get_enclosing_field("smap.value.name"));
    EXPECT_EQ("smap", _matching_elems_fields->get_enclosing_field("smap.value.val"));
    EXPECT_EQ("smap", _matching_elems_fields->get_enclosing_field("smap.value.fval"));
}

TEST_F(AttributeCombinerTest, require_that_matching_elems_fields_is_setup_for_filtered_map_of_string)
{
    set_field("map", true);
    EXPECT_TRUE(_matching_elems_fields);
    EXPECT_FALSE(_matching_elems_fields->has_field("array"));
    EXPECT_TRUE(_matching_elems_fields->has_field("map"));
    EXPECT_FALSE(_matching_elems_fields->has_field("smap"));
    EXPECT_EQ("", _matching_elems_fields->get_enclosing_field("map.foo"));
    EXPECT_EQ("map", _matching_elems_fields->get_enclosing_field("map.key"));
    EXPECT_EQ("map", _matching_elems_fields->get_enclosing_field("map.value"));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
