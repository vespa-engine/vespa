// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/test/tensor_divergence.h>
#include <vespa/searchsummary/docsummary/attributedfw.h>
#include <vespa/searchsummary/docsummary/summary_elements_selector.h>
#include <vespa/searchsummary/test/mock_attribute_manager.h>
#include <vespa/searchsummary/test/mock_state_callback.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP("attributedfw_test");

using search::MatchingElements;
using search::MatchingElementsFields;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::docsummary::AttributeDFWFactory;
using search::docsummary::DocsumFieldWriter;
using search::docsummary::GetDocsumsState;
using search::docsummary::SummaryElementsSelector;
using search::docsummary::test::MockAttributeManager;
using search::docsummary::test::MockStateCallback;
using search::docsummary::test::SlimeValue;
using search::test::compute_tensor_nrmse;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;

using ElementVector = std::vector<uint32_t>;

std::vector<char> as_vector(std::string_view value) {
    return {value.data(), value.data() + value.size()};
}

const std::string dense_tensor_spec = "tensor(x[4])";

[[nodiscard]] std::unique_ptr<Value> tensor_from_spec(const TensorSpec& spec) {
    return value_from_spec(spec, FastValueBuilderFactory::get());
}

[[nodiscard]] std::unique_ptr<Value> make_tensor(const double x1, const double x3) {
    return tensor_from_spec(TensorSpec(dense_tensor_spec).add({{"x", 1}}, x1).add({{"x", 3}}, x3));
}

class AttributeDFWTest : public ::testing::Test {
protected:
    MockAttributeManager                     _attrs;
    std::unique_ptr<DocsumFieldWriter>       _writer;
    std::shared_ptr<MatchingElementsFields>  _matching_elements_fields;
    MockStateCallback                        _callback;
    GetDocsumsState                          _state;
    std::unique_ptr<SummaryElementsSelector> _elements_selector;
    std::string                              _field_name;

public:
    AttributeDFWTest()
        : _attrs(),
          _writer(),
          _matching_elements_fields(std::make_shared<MatchingElementsFields>()),
          _callback(),
          _state(_callback),
          _elements_selector(),
          _field_name() {
        _attrs.build_string_attribute("array_str", {{"a", "b", "c"}, {}});
        _attrs.build_int_attribute("array_int", BasicType::INT32, {{10, 20, 30}, {}});
        _attrs.build_float_attribute("array_float", {{10.5, 20.5, 30.5}, {}});

        _attrs.build_string_attribute("wset_str", {{"a", "b", "c"}, {}}, CollectionType::WSET);
        _attrs.build_int_attribute("wset_int", BasicType::INT32, {{10, 20, 30}, {}}, CollectionType::WSET);
        _attrs.build_float_attribute("wset_float", {{10.5, 20.5, 30.5}, {}}, CollectionType::WSET);
        _attrs.build_bool_attribute("array_bool", {{1, 0, 1}, {}});

        _attrs.build_string_attribute("single_str", {{"world"}, {}}, CollectionType::SINGLE);
        _attrs.build_raw_attribute("single_raw", {{as_vector("hello")}, {}});

        std::vector<std::unique_ptr<Value>> tensors;
        tensors.emplace_back(make_tensor(4, 2));
        tensors.emplace_back(make_tensor(10, 20));

        _attrs.build_tensor_attribute("dense_tensor", dense_tensor_spec, false, tensors);
        _attrs.build_tensor_attribute("quantized_dense_tensor", dense_tensor_spec, true, tensors);
        _state._attrCtx = _attrs.mgr().createContext();
        _state._matching_elements_fields = _matching_elements_fields;
    }
    ~AttributeDFWTest() override;

    void setup(const std::string& field_name, bool filter_elements) {
        if (filter_elements) {
            _elements_selector =
                std::make_unique<SummaryElementsSelector>(SummaryElementsSelector::select_by_match(field_name, {}));
        } else {
            _elements_selector = std::make_unique<SummaryElementsSelector>(SummaryElementsSelector::select_all());
        }
        _writer = AttributeDFWFactory::create(_attrs.mgr(), field_name);
        _writer->setIndex(0);
        auto attr = _state._attrCtx->getAttribute(field_name);
        if (attr->hasMultiValue() || (attr->getBasicType() == BasicType::TENSOR)) {
            EXPECT_TRUE(_writer->setFieldWriterStateIndex(0));
            _state._fieldWriterStates.resize(1);
        } else {
            EXPECT_FALSE(_writer->setFieldWriterStateIndex(0));
        }
        _field_name = field_name;
        _state._attributes.resize(1);
        _state._attributes[0] = attr;
    }

    [[nodiscard]] vespalib::Slime field_to_slime(uint32_t docid) {
        vespalib::Slime                act;
        vespalib::slime::SlimeInserter inserter(act);
        if (!_writer->isDefaultValue(docid, _state)) {
            _writer->insert_field(docid, nullptr, _state, _elements_selector->get_selected_elements(docid, _state),
                                  inserter);
        }
        return act;
    }

    [[nodiscard]] static std::unique_ptr<Value> decode_slime_to_tensor(const vespalib::Slime& obj) {
        auto                buf = obj.get().asData();
        vespalib::nbostream in_stream(buf.data, buf.size);
        return vespalib::eval::decode_value(in_stream, FastValueBuilderFactory::get());
    }

    void expect_field(const std::string& exp_slime_as_json, uint32_t docid) {
        vespalib::Slime act = field_to_slime(docid);
        SlimeValue      exp(exp_slime_as_json);
        EXPECT_EQ(exp.slime, act);
    }

    void expect_filtered(const ElementVector& matching_elems, const std::string& exp_slime_as_json,
                         uint32_t docid = 1) {
        _callback.clear();
        _callback.add_matching_elements(docid, _field_name, matching_elems);
        _state._matching_elements = std::unique_ptr<MatchingElements>();
        _state._fieldWriterStates[0] = nullptr; // Force new state to pick up changed matching elements
        expect_field(exp_slime_as_json, docid);
    }
};

AttributeDFWTest::~AttributeDFWTest() = default;

TEST_F(AttributeDFWTest, outputs_slime_for_array_of_string) {
    setup("array_str", false);
    expect_field("[ 'a', 'b', 'c' ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_array_of_int) {
    setup("array_int", false);
    expect_field("[ 10, 20, 30 ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_array_of_float) {
    setup("array_float", false);
    expect_field("[ 10.5, 20.5, 30.5 ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_array_of_bool) {
    setup("array_bool", false);
    expect_field("[ true, false, true ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, filters_matched_elements_in_array_bool_attribute) {
    setup("array_bool", true);
    expect_filtered({}, "null");
    expect_filtered({0}, "[ true ]");
    expect_filtered({1, 2}, "[ false, true ]");
    expect_filtered({3}, "null");
}

TEST_F(AttributeDFWTest, outputs_slime_for_wset_of_string) {
    setup("wset_str", false);
    expect_field("[ {'item':'a', 'weight':1}, {'item':'b', 'weight':1}, {'item':'c', 'weight':1} ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_wset_of_int) {
    setup("wset_int", false);
    expect_field("[ {'item':10, 'weight':1}, {'item':20, 'weight':1}, {'item':30, 'weight':1} ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, outputs_slime_for_wset_of_float) {
    setup("wset_float", false);
    expect_field("[ {'item':10.5, 'weight':1}, {'item':20.5, 'weight':1}, {'item':30.5, 'weight':1} ]", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, matched_elements_fields_is_populated) {
    setup("array_str", true);
    MatchingElementsFields matching_elements_fields;
    _elements_selector->maybe_apply_to(matching_elements_fields);
    EXPECT_TRUE(matching_elements_fields.has_field("array_str"));
}

TEST_F(AttributeDFWTest, filters_matched_elements_in_array_attribute) {
    setup("array_str", true);
    expect_filtered({}, "null");
    expect_filtered({0}, "[ 'a' ]");
    expect_filtered({1, 2}, "[ 'b', 'c' ]");
    expect_filtered({3}, "null");
}

TEST_F(AttributeDFWTest, filters_matched_elements_in_wset_attribute) {
    setup("wset_str", true);
    expect_filtered({}, "null");
    expect_filtered({0}, "[ {'item':'a', 'weight':1} ]");
    expect_filtered({1, 2}, "[ {'item':'b', 'weight':1}, {'item':'c', 'weight':1} ]");
    expect_filtered({3}, "null");
}

TEST_F(AttributeDFWTest, single_string) {
    setup("single_str", false);
    expect_field(R"("world")", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, single_value_raw) {
    setup("single_raw", false);
    expect_field("x68656C6C6F", 1);
    expect_field("null", 2);
}

TEST_F(AttributeDFWTest, tensors_are_rendered_as_encoded_data_field) {
    setup("dense_tensor", false);
    // To be able to actually compare the rendered tensors, we have to decode the output
    const auto t1 = decode_slime_to_tensor(field_to_slime(1));
    EXPECT_EQ(*t1, *make_tensor(4, 2));
    const auto t2 = decode_slime_to_tensor(field_to_slime(2));
    EXPECT_EQ(*t2, *make_tensor(10, 20));
}

TEST_F(AttributeDFWTest, quantized_tensor_cells_are_dequantized_and_rendered_as_encoded_data_field) {
    setup("quantized_dense_tensor", false);
    // Quantized tensors are approximations, so we have to compare within a max error bound
    constexpr double max_divergence = 0.0125;

    const auto t1 = decode_slime_to_tensor(field_to_slime(1));
    const auto expected_t1 = make_tensor(4, 2);
    EXPECT_LE(compute_tensor_nrmse(*expected_t1, *t1), max_divergence);

    const auto t2 = decode_slime_to_tensor(field_to_slime(2));
    const auto expected_t2 = make_tensor(10, 20);
    EXPECT_LE(compute_tensor_nrmse(*expected_t2, *t2), max_divergence);
}

GTEST_MAIN_RUN_ALL_TESTS()
