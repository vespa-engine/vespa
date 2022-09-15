// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchsummary/docsummary/attributedfw.h>
#include <vespa/searchsummary/test/mock_attribute_manager.h>
#include <vespa/searchsummary/test/mock_state_callback.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("attributedfw_test");

using search::MatchingElements;
using search::MatchingElementsFields;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::docsummary::AttributeDFWFactory;
using search::docsummary::GetDocsumsState;
using search::docsummary::DocsumFieldWriter;
using search::docsummary::test::MockAttributeManager;
using search::docsummary::test::MockStateCallback;
using search::docsummary::test::SlimeValue;

using ElementVector = std::vector<uint32_t>;

class AttributeDFWTest : public ::testing::Test {
protected:
    MockAttributeManager _attrs;
    std::unique_ptr<DocsumFieldWriter> _writer;
    MockStateCallback _callback;
    GetDocsumsState _state;
    std::shared_ptr<search::MatchingElementsFields> _matching_elems_fields;
    vespalib::string _field_name;

public:
    AttributeDFWTest()
        : _attrs(),
          _writer(),
          _callback(),
          _state(_callback),
          _matching_elems_fields(),
          _field_name()
    {
        _attrs.build_string_attribute("array_str", { {"a", "b", "c"}, {} });
        _attrs.build_int_attribute("array_int", BasicType::INT32, { {10, 20, 30}, {} });
        _attrs.build_float_attribute("array_float", { {10.5, 20.5, 30.5}, {} });

        _attrs.build_string_attribute("wset_str", { {"a", "b", "c"}, {} }, CollectionType::WSET);
        _attrs.build_int_attribute("wset_int", BasicType::INT32, { {10, 20, 30}, {} }, CollectionType::WSET);
        _attrs.build_float_attribute("wset_float", { {10.5, 20.5, 30.5}, {} }, CollectionType::WSET);

        _state._attrCtx = _attrs.mgr().createContext();
    }
    ~AttributeDFWTest() {}

    void setup(const vespalib::string& field_name, bool filter_elements) {
        if (filter_elements) {
            _matching_elems_fields = std::make_shared<MatchingElementsFields>();
        }
        _writer = AttributeDFWFactory::create(_attrs.mgr(), field_name, filter_elements, _matching_elems_fields);
        _writer->setIndex(0);
        EXPECT_TRUE(_writer->setFieldWriterStateIndex(0));
        _state._fieldWriterStates.resize(1);
        _field_name = field_name;
        _state._attributes.resize(1);
        _state._attributes[0] = _state._attrCtx->getAttribute(field_name);
    }

    void expect_field(const vespalib::string& exp_slime_as_json, uint32_t docid) {
        vespalib::Slime act;
        vespalib::slime::SlimeInserter inserter(act);
        _writer->insertField(docid, nullptr, _state, inserter);

        SlimeValue exp(exp_slime_as_json);
        EXPECT_EQ(exp.slime, act);
    }

    void expect_filtered(const ElementVector& matching_elems, const std::string& exp_slime_as_json, uint32_t docid = 1) {
        _callback.clear();
        _callback.add_matching_elements(docid, _field_name, matching_elems);
        _state._matching_elements = std::unique_ptr<MatchingElements>();
        _state._fieldWriterStates[0] = nullptr; // Force new state to pick up changed matching elements
        expect_field(exp_slime_as_json, docid);
    }
};

TEST_F(AttributeDFWTest, outputs_slime_for_array_of_string)
{
    setup("array_str", false);
    expect_field("[ 'a', 'b', 'c' ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_array_of_int)
{
    setup("array_int", false);
    expect_field("[ 10, 20, 30 ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_array_of_float)
{
    setup("array_float", false);
    expect_field("[ 10.5, 20.5, 30.5 ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_wset_of_string)
{
    setup("wset_str", false);
    expect_field("[ {'item':'a', 'weight':1}, {'item':'b', 'weight':1}, {'item':'c', 'weight':1} ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_wset_of_int)
{
    setup("wset_int", false);
    expect_field("[ {'item':10, 'weight':1}, {'item':20, 'weight':1}, {'item':30, 'weight':1} ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_wset_of_float)
{
    setup("wset_float", false);
    expect_field("[ {'item':10.5, 'weight':1}, {'item':20.5, 'weight':1}, {'item':30.5, 'weight':1} ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, matched_elements_fields_is_populated)
{
    setup("array_str", true);
    EXPECT_TRUE(_matching_elems_fields->has_field("array_str"));
}

TEST_F(AttributeDFWTest, filteres_matched_elements_in_array_attribute)
{
    setup("array_str", true);
    expect_filtered({}, "null");
    expect_filtered({0}, "[ 'a' ]");
    expect_filtered({1, 2}, "[ 'b', 'c' ]");
    expect_filtered({3}, "null");
}

TEST_F(AttributeDFWTest, filteres_matched_elements_in_wset_attribute)
{
    setup("wset_str", true);
    expect_filtered({}, "null");
    expect_filtered({0}, "[ {'item':'a', 'weight':1} ]");
    expect_filtered({1, 2}, "[ {'item':'b', 'weight':1}, {'item':'c', 'weight':1} ]");
    expect_filtered({3}, "null");
}

GTEST_MAIN_RUN_ALL_TESTS()
