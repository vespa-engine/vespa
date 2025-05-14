// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summary_elements_selector.h"
#include <vespa/searchlib/common/matching_elements_fields.h>

namespace search::docsummary {

SummaryElementsSelector::SummaryElementsSelector()
    : _selector(Selector::ALL),
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
SummaryElementsSelector::select_by_match(std::vector<std::string> struct_fields)
{
    SummaryElementsSelector elements_selector;
    elements_selector._selector = Selector::BY_MATCH;
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
SummaryElementsSelector::apply_to(const std::string& field_name, MatchingElementsFields& target) const
{
    if (_struct_fields.empty()) {
        target.add_field(field_name);
    } else {
        for (auto &struct_field : _struct_fields) {
            target.add_mapping(field_name, struct_field);
        }
    }

}

}
