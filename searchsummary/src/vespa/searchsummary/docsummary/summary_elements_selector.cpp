// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summary_elements_selector.h"

namespace search::docsummary {

SummaryElementsSelector::SummaryElementsSelector()
    : _selector(Selector::ALL),
      _matching_elements_fields(),
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
SummaryElementsSelector::select_by_match()
{
    SummaryElementsSelector elements_selector;
    elements_selector._selector = Selector::BY_MATCH;
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


}
