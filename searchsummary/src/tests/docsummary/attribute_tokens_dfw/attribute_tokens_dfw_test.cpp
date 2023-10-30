// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchsummary/docsummary/attribute_tokens_dfw.h>
#include <vespa/searchsummary/test/mock_attribute_manager.h>
#include <vespa/searchsummary/test/mock_state_callback.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_tokens_dfw_test");

using search::attribute::CollectionType;
using search::docsummary::AttributeTokensDFW;
using search::docsummary::GetDocsumsState;
using search::docsummary::DocsumFieldWriter;
using search::docsummary::test::MockAttributeManager;
using search::docsummary::test::MockStateCallback;
using search::docsummary::test::SlimeValue;

class AttributeTokensDFWTest : public ::testing::Test {
protected:
    MockAttributeManager _attrs;
    std::unique_ptr<DocsumFieldWriter> _writer;
    MockStateCallback _callback;
    GetDocsumsState _state;
    std::shared_ptr<search::MatchingElementsFields> _matching_elems_fields;
    vespalib::string _field_name;

public:
    AttributeTokensDFWTest()
        : _attrs(),
          _writer(),
          _callback(),
          _state(_callback),
          _matching_elems_fields(),
          _field_name()
    {
        _attrs.build_string_attribute("array_str", { {"This", "is", "A TEST"}, {} });
        _attrs.build_string_attribute("cased_array_str", { {"CASING", "Matters here" }, {} }, CollectionType::ARRAY, false);
        _attrs.build_string_attribute("wset_str", { {"This is", "b", "C"}, {} }, CollectionType::WSET);
        _attrs.build_string_attribute("single_str", { {"Hello World"}, {} }, CollectionType::SINGLE);
        _state._attrCtx = _attrs.mgr().createContext();
    }
    ~AttributeTokensDFWTest() {}

    void setup(const vespalib::string& field_name) {
        _writer = std::make_unique<AttributeTokensDFW>(field_name);
        _writer->setIndex(0);
        auto attr = _state._attrCtx->getAttribute(field_name);
        EXPECT_TRUE(_writer->setFieldWriterStateIndex(0));
        _state._fieldWriterStates.resize(1);
        _field_name = field_name;
        _state._attributes.resize(1);
        _state._attributes[0] = attr;
    }

    void expect_field(const vespalib::string& exp_slime_as_json, uint32_t docid) {
        vespalib::Slime act;
        vespalib::slime::SlimeInserter inserter(act);
        if (!_writer->isDefaultValue(docid, _state)) {
            _writer->insertField(docid, nullptr, _state, inserter);
        }

        SlimeValue exp(exp_slime_as_json);
        EXPECT_EQ(exp.slime, act);
    }
};

TEST_F(AttributeTokensDFWTest, outputs_slime_for_array_of_string)
{
    setup("array_str");
    expect_field("[ ['this' ], [ 'is' ], [ 'a test' ] ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeTokensDFWTest, outputs_slime_for_cased_array_of_string)
{
    setup("cased_array_str");
    expect_field("[ ['CASING' ], [ 'Matters here' ] ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeTokensDFWTest, outputs_slime_for_wset_of_string)
{
    setup("wset_str");
    expect_field("[ ['this is'], [ 'b' ], [ 'c' ] ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeTokensDFWTest, single_string)
{
    setup("single_str");
    expect_field("[ 'hello world' ]", 1);
    expect_field("[ '' ]", 2);
}

GTEST_MAIN_RUN_ALL_TESTS()
