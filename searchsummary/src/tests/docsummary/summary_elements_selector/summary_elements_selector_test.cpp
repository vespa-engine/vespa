// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/element_ids.h>
#include <vespa/searchsummary/docsummary/summary_elements_selector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/featureset.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <memory>

using search::MatchingElements;
using search::MatchingElementsFields;
using search::docsummary::ElementIds;
using search::docsummary::GetDocsumsState;
using search::docsummary::GetDocsumsStateCallback;
using search::docsummary::SummaryElementsSelector;
using vespalib::FeatureSet;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;

using ElementVector = std::vector<uint32_t>;

namespace {

constexpr uint32_t doc_id = 2;
const std::string elementwise_bm25("elementwise(bm25(field),x,double)");

std::unique_ptr<Value>
make_feature(const std::vector<uint32_t>& element_ids)
{
    TensorSpec spec("tensor(x{})");
    for (auto id : element_ids) {
        spec.add({{"x", std::to_string(id)}}, 1.0);
    }
    return vespalib::eval::value_from_spec(spec, FastValueBuilderFactory::get());
}

class StateCallback : public GetDocsumsStateCallback {
private:
    std::string   _field_name;
    ElementVector _matching_elements;
    std::unique_ptr<Value>  _feature;

public:
    StateCallback(const std::string& field_name, const ElementVector& matching_elements, std::unique_ptr<Value> feature)
        : _field_name(field_name),
          _matching_elements(matching_elements),
          _feature(std::move(feature))
    {
    }
    ~StateCallback() override;
    void fillSummaryFeatures(GetDocsumsState&) override;
    void fillRankFeatures(GetDocsumsState&) override;
    std::unique_ptr<MatchingElements> fill_matching_elements(const MatchingElementsFields&) override;
};

StateCallback::~StateCallback() = default;

void
StateCallback::fillSummaryFeatures(GetDocsumsState& state)
{
    if (_feature) {
        auto feature_set = std::make_shared<FeatureSet>(std::vector<std::string>{elementwise_bm25},
                                                        1);
        auto dst = feature_set->getFeaturesByIndex(feature_set->addDocId(doc_id));
        vespalib::nbostream buf;
        encode_value(*_feature, buf);
        dst->set_data(vespalib::Memory(buf.peek(), buf.size()));
        state._summaryFeatures = feature_set;
    } else {
        state._summaryFeatures = std::make_shared<FeatureSet>(std::vector<std::string>{}, 1);
    }
}

void
StateCallback::fillRankFeatures(GetDocsumsState&)
{
}

std::unique_ptr<MatchingElements>
StateCallback::fill_matching_elements(const MatchingElementsFields& matching_elements_fields)
{
    auto result = std::make_unique<MatchingElements>();
    if (matching_elements_fields.has_field(_field_name)) {
        result->add_matching_elements(doc_id, matching_elements_fields.enclosing_field(_field_name), _matching_elements);
    }
    return result;
}

class StandaloneElementIds {
    std::optional<std::vector<uint32_t>> _ids;
    struct ctor_tag {};
public:
    explicit StandaloneElementIds(ctor_tag) noexcept
        : _ids()
    {}
    explicit StandaloneElementIds(const std::vector<uint32_t>& ids) noexcept
        : _ids(ids)
    {}
    explicit StandaloneElementIds(ElementIds element_ids)
        : _ids()
    {
        if (!element_ids.all_elements()) {
            _ids.emplace(element_ids.begin(), element_ids.end());
        }
    }
    static StandaloneElementIds all() noexcept { return StandaloneElementIds(ctor_tag()); }
    static StandaloneElementIds none() noexcept { return StandaloneElementIds(std::vector<uint32_t>()); }
    auto operator<=>(const StandaloneElementIds&) const noexcept = default;
    auto& get_ids() const noexcept { return _ids; }
};

void PrintTo(const StandaloneElementIds& v, std::ostream* os)
{
    *os << ::testing::PrintToString(v.get_ids());
}

}

class SummaryElementsSelectorTest : public ::testing::Test {
public:
    SummaryElementsSelectorTest()
        : ::testing::Test()
    {
    }
    ~SummaryElementsSelectorTest() override;
    StandaloneElementIds get_selected_elements(const SummaryElementsSelector& selector, const std::string& field_name,
                                               const std::vector<uint32_t>& element_ids,
                                               std::unique_ptr<Value> feature) noexcept;
    StandaloneElementIds get_all() noexcept;
    StandaloneElementIds get_by_match(const std::string& field_name, const std::vector<uint32_t>& element_ids) noexcept;
    StandaloneElementIds get_by_summary_feature(std::unique_ptr<Value> feature) noexcept;
};

SummaryElementsSelectorTest::~SummaryElementsSelectorTest() = default;

StandaloneElementIds
SummaryElementsSelectorTest::get_selected_elements(const SummaryElementsSelector& selector,
                                                   const std::string& field_name,
                                                   const std::vector<uint32_t>& element_ids,
                                                   std::unique_ptr<Value> feature) noexcept
{
    StateCallback callback(field_name, element_ids, std::move(feature));
    GetDocsumsState state(callback);
    auto matching_elements_fields = std::make_shared<MatchingElementsFields>();
    selector.maybe_apply_to(*matching_elements_fields);
    state._matching_elements_fields = matching_elements_fields;
    return StandaloneElementIds(selector.get_selected_elements(doc_id, state));
}

StandaloneElementIds
SummaryElementsSelectorTest::get_all() noexcept
{
    return get_selected_elements(SummaryElementsSelector::select_all(), "field", {}, {});
}

StandaloneElementIds
SummaryElementsSelectorTest::get_by_match(const std::string& field_name, const std::vector<uint32_t>& element_ids) noexcept
{
    return get_selected_elements(SummaryElementsSelector::select_by_match("field", {"field.sub"}),
                                 field_name, element_ids, {});
}

StandaloneElementIds
SummaryElementsSelectorTest::get_by_summary_feature(std::unique_ptr<Value> feature) noexcept
{
    return get_selected_elements(SummaryElementsSelector::select_by_summary_feature(elementwise_bm25),
                                 "nofield", {}, std::move(feature));
}

TEST_F(SummaryElementsSelectorTest, all)
{
    EXPECT_EQ(StandaloneElementIds::all(), get_all());
}

TEST_F(SummaryElementsSelectorTest, by_match)
{
    EXPECT_EQ(StandaloneElementIds::none(), get_by_match("field", {}));
    EXPECT_EQ(StandaloneElementIds({1,2,3}), get_by_match("field", {1,2,3}));
    EXPECT_EQ(StandaloneElementIds({1,2,3}), get_by_match("field.sub", {1,2,3}));
    EXPECT_EQ(StandaloneElementIds::none(), get_by_match("field.notsub", {1,2,3}));
    EXPECT_EQ(StandaloneElementIds::none(), get_by_match("ofield", {1,2,3}));
}

TEST_F(SummaryElementsSelectorTest, by_summary_feature)
{
    EXPECT_EQ(StandaloneElementIds::none(), get_by_summary_feature({}));
    EXPECT_EQ(StandaloneElementIds({1,2,3}), get_by_summary_feature(make_feature({1, 2, 3})));
    EXPECT_EQ(StandaloneElementIds({4, 9}), get_by_summary_feature(make_feature({4, 9})));
}

GTEST_MAIN_RUN_ALL_TESTS()
