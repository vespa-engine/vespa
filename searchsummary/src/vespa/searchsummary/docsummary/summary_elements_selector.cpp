// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summary_elements_selector.h"
#include "docsumstate.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <algorithm>
#include <cassert>
#include <charconv>
#include <optional>

using search::common::ElementIds;
using vespalib::FeatureSet;
using vespalib::SharedStringRepo;
using vespalib::eval::FastValueBuilderFactory;

namespace search::docsummary {

namespace {

std::vector<uint32_t> empty;

std::vector<uint32_t>
extract_elements_from_feature(const FeatureSet::Value& encoded_value)
{
    std::vector<uint32_t> elements;
    if (encoded_value.is_data()) {
        auto data = encoded_value.as_data();
        vespalib::nbostream buf(data.data, data.size);
        auto value = vespalib::eval::decode_value(buf, FastValueBuilderFactory::get());
        if (value->type().count_mapped_dimensions() == 1) {
            vespalib::string_id addr;
            vespalib::string_id *addr_ref(&addr);
            std::span addr_ref_span(&addr_ref, 1);
            size_t subspace = 0;
            size_t num_subspaces_visited = 0;
            uint32_t num_subspaces = value->index().size();
            elements.reserve(num_subspaces);
            auto view = value->index().create_view({});
            view->lookup({});
            while (view->next_result(addr_ref_span, subspace)) {
                assert(subspace < num_subspaces);
                auto label = SharedStringRepo::Handle::string_from_id(addr);
                uint32_t ivalue(0);
                std::from_chars(label.data(), label.data() + label.size(), ivalue);
                elements.emplace_back(ivalue);
                ++num_subspaces_visited;
            }
            assert(num_subspaces_visited == num_subspaces);
            std::sort(elements.begin(), elements.end());
        }
    }
    return elements;
}

}

SummaryElementsSelector::SummaryElementsSelector()
    : _selector(Selector::ALL),
      _field(),
      _struct_fields(),
      _summary_feature()
{
}

SummaryElementsSelector::SummaryElementsSelector(const SummaryElementsSelector&) = default;
SummaryElementsSelector::SummaryElementsSelector(SummaryElementsSelector&&) noexcept = default;
SummaryElementsSelector::~SummaryElementsSelector() = default;

SummaryElementsSelector
SummaryElementsSelector::select_all()
{
    return {};
}

SummaryElementsSelector
SummaryElementsSelector::select_by_match(const std::string& field, std::vector<std::string> struct_fields)
{
    SummaryElementsSelector elements_selector;
    elements_selector._selector = Selector::BY_MATCH;
    elements_selector._field = field;
    elements_selector._struct_fields = std::move(struct_fields);
    return elements_selector;
}

SummaryElementsSelector
SummaryElementsSelector::select_by_summary_feature(const std::string& summary_feature)
{
    SummaryElementsSelector elements_selector;
    elements_selector._selector = Selector::BY_SUMMARY_FEATURE;
    elements_selector._summary_feature = summary_feature;
    return elements_selector;
}

void
SummaryElementsSelector::apply_to(MatchingElementsFields& target) const
{
    target.add_field(_field);
    for (auto &struct_field : _struct_fields) {
        target.add_mapping(_field, struct_field);
    }
}

ElementIds
SummaryElementsSelector::get_selected_elements(uint32_t docid, GetDocsumsState &state) const
{
    switch (_selector) {
        case Selector::ALL:
            return ElementIds::select_all();
        case Selector::BY_MATCH:
            return ElementIds(state.get_matching_elements().get_matching_elements(docid, _field));
        case Selector::BY_SUMMARY_FEATURE:
           return ElementIds(get_summary_feature_elements(docid, state));
        default:
            return ElementIds(empty);
    }
}

const std::vector<uint32_t>&
SummaryElementsSelector::get_summary_feature_elements(uint32_t docid, GetDocsumsState& state) const
{
    auto& feature_set = state.get_summary_features();
    if (!state._summary_features_elements) {
        state._summary_features_elements = std::make_unique<MatchingElements>();
    }
    if (!state._summary_features_elements_keys.contains(_summary_feature)) {
        state._summary_features_elements_keys.insert(_summary_feature);
        auto name_idx = feature_set.get_name_idx(_summary_feature);
        if (name_idx.has_value()) {
            for (uint32_t docid_idx = 0; docid_idx < feature_set.numDocs(); ++docid_idx) {
                auto feature_docid = feature_set.get_docids()[docid_idx];
                auto& encoded_value = feature_set.getFeaturesByIndex(docid_idx)[name_idx.value()];
                auto elements = extract_elements_from_feature(encoded_value);
                state._summary_features_elements->add_matching_elements(feature_docid, _summary_feature, elements);
            }
        }
    }
    return state._summary_features_elements->get_matching_elements(docid, _summary_feature);
}

}
