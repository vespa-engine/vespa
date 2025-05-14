// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>

namespace search { class MatchingElementsFields; }

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
    std::vector<std::string> _struct_fields;
    std::string            _summary_feature;
    SummaryElementsSelector();
public:
    SummaryElementsSelector(const SummaryElementsSelector& rhs);
    SummaryElementsSelector(SummaryElementsSelector&& rhs) noexcept;
    ~SummaryElementsSelector();
    bool matched_elements_only() const noexcept { return _selector == Selector::BY_MATCH; }
    void consider_apply_to(const std::string& field_name, MatchingElementsFields& target) const {
        if (_selector == Selector::BY_MATCH) {
            apply_to(field_name, target);
        }
    }
    void apply_to(const std::string& field_name, MatchingElementsFields& target) const;
    static SummaryElementsSelector select_all();
    static SummaryElementsSelector select_by_match(std::vector<std::string> struct_fields);
    static SummaryElementsSelector select_by_summary_feature(const std::string& summary_feature);
};

}
