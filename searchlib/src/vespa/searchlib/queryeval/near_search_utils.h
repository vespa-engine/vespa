// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "match_span.h"
#include <vector>

namespace search::queryeval::near_search_utils {

/*
 * Class used by near search when only checking for a match.
 */
class BoolMatchResult {
    bool _is_match;
public:
    BoolMatchResult()
        : _is_match(false)
    { }
    void register_match(uint32_t element_id) noexcept {
        (void) element_id;
        _is_match = true;
    }
    static constexpr bool shortcut_return = true;
    static constexpr bool collect_spans = false;
    bool is_match() const noexcept { return _is_match; }
};

/*
 * Class used by near search when collecting matching element ids.
 */
class ElementIdMatchResult {
    std::vector<uint32_t>& _element_ids;
    bool                   _need_sort;
public:
    ElementIdMatchResult(std::vector<uint32_t>& element_ids)
        : _element_ids(element_ids),
          _need_sort(false)
    {
    }
    void register_match(uint32_t element_id) {
        if (_element_ids.empty()) {
            _element_ids.push_back(element_id);
        } else if (_element_ids.back() != element_id) {
            if (_element_ids.back() > element_id) {
                _need_sort = true;
            }
            _element_ids.push_back(element_id);
        }
    }
    static constexpr bool shortcut_return = false;
    static constexpr bool collect_spans = false;
    void maybe_sort_element_ids();
};

class SpanMatchResult {
    std::vector<MatchSpan>& _match_spans;
public:
    SpanMatchResult(std::vector<MatchSpan>& match_spans)
        : _match_spans(match_spans)
    {
    }
    void register_match(const MatchSpan& match_span) {
        if (_match_spans.empty() || _match_spans.back().field_id() != match_span.field_id() || _match_spans.back().last() < match_span.first()) {
            _match_spans.push_back(match_span);
        } else {
            _match_spans.back().merge_spans(match_span);
        }
    }
    static constexpr bool shortcut_return = false;
    static constexpr bool collect_spans = true;
};

}
