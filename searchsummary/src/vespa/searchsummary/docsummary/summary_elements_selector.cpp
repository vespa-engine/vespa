// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summary_elements_selector.h"
#include "docsumstate.h"
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>

namespace search::docsummary {

namespace {

std::vector<uint32_t> empty;

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

const std::vector<uint32_t> *
SummaryElementsSelector::get_selected_elements(uint32_t docid, GetDocsumsState &state) const
{
    switch (_selector) {
        case Selector::ALL:
            return nullptr;
        case Selector::BY_MATCH:
            return &state.get_matching_elements().get_matching_elements(docid, _field);
        case Selector::BY_SUMMARY_FEATURE:
        default:
            return &empty;
    }
}

}
