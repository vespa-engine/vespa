// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
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
    void maybe_sort_element_ids();
};

}
