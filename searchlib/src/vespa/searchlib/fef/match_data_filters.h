// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/element_ids.h>
#include <vespa/searchlib/queryeval/match_span.h>

namespace search::fef {

/*
 * Filter for match data that doesn't filter anything.
 */
class NoMatchDataFilter {
public:
    NoMatchDataFilter() noexcept { }
    void new_field() noexcept { }
    bool is_filtered(const streaming::Hit&) const noexcept { return false; }
};

/*
 * Filter for match data that only passes specified elements.
 */
class ElementIdMatchDataFilter {
    common::ElementIds           _element_ids;
    common::ElementIds::iterator _element_ids_it;
public:
    explicit ElementIdMatchDataFilter(common::ElementIds element_ids) noexcept
        : _element_ids(element_ids),
          _element_ids_it()
    {
    }

    void new_field() noexcept {
        _element_ids_it = _element_ids.begin();
    }

    bool is_filtered(const streaming::Hit& hit) noexcept {
        while (_element_ids_it != _element_ids.end() && *_element_ids_it < hit.element_id()) {
            ++_element_ids_it;
        }
        return (_element_ids_it == _element_ids.end() || *_element_ids_it != hit.element_id());
    }
};

/*
 * Filter for match data that passes match data within the match spans.
 */
class MatchSpanMatchDataFilter {
    std::span<const queryeval::MatchSpan> _match_spans;
    std::span<const queryeval::MatchSpan>::iterator _match_spans_it;
public:
    explicit MatchSpanMatchDataFilter(std::span<const queryeval::MatchSpan> match_spans) noexcept
        : _match_spans(match_spans),
          _match_spans_it(match_spans.begin())
    {
    }

    // Match span contains field id, no iterator rewinding required.
    void new_field() noexcept { }

    bool is_filtered(const streaming::Hit& hit) noexcept {
        while (_match_spans_it != _match_spans.end() && _match_spans_it->is_before(hit)) {
            ++_match_spans_it;
        }
        return (_match_spans_it == _match_spans.end() || _match_spans_it->is_after(hit));
    }
};

}
