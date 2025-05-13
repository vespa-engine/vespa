// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/matching_elements_fields.h>

namespace search::docsummary {

/*
 * A class selecting which summary elements of a multi-value field to render.
 */
class SummaryElementsSelector {
    enum class Selector {
        ALL,
        BY_MATCH,
        BY_SUMMARY_FEATURE
    };

    Selector               _selector;
    MatchingElementsFields _matching_elements_fields;
    std::string            _summary_feature;
    SummaryElementsSelector();
public:
    SummaryElementsSelector(const SummaryElementsSelector& rhs);
    SummaryElementsSelector(SummaryElementsSelector&& rhs) noexcept;
    ~SummaryElementsSelector();
    bool matched_elements_only() const noexcept { return _selector == Selector::BY_MATCH; }
    MatchingElementsFields& matching_elements_fields() noexcept { return _matching_elements_fields; }
    const MatchingElementsFields& matching_elements_fields() const noexcept { return _matching_elements_fields; }
    void merge_matching_elements_fields_to(MatchingElementsFields& merged_matching_element_fields) const {
        if (_selector == Selector::BY_MATCH) {
            merged_matching_element_fields.merge(_matching_elements_fields);
        }
    }
    static SummaryElementsSelector select_all();
    static SummaryElementsSelector select_by_match();
    static SummaryElementsSelector select_by_summary_feature(const std::string& summary_feature);
};

}
