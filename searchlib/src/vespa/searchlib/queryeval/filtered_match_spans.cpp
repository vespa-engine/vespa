// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filtered_match_spans.h"
#include <vespa/searchcommon/common/element_ids.h>
#include <algorithm>
#include <limits>
#include <optional>

using search::common::ElementIds;

namespace search::queryeval {

namespace {

class ElementIdMatchSpanFilter {
    std::span<const uint32_t>           _element_ids;
    std::span<const uint32_t>::iterator _element_ids_itr;
    MatchSpanPos                        _first;
    MatchSpanPos                        _last;
    bool                                _valid;
public:
    ElementIdMatchSpanFilter(std::span<const uint32_t> element_ids) noexcept
        : _element_ids(element_ids),
          _element_ids_itr(),
          _first(MatchSpanPos(0, 0)),
          _last(MatchSpanPos(0, 0)),
          _valid(false)
    {
    }
    void new_field() noexcept {
        _element_ids_itr = _element_ids.begin();
        step();
    }
    void step() noexcept
    {
        _valid = false;
        if (_element_ids_itr != _element_ids.end()) {
            _valid = true;
            _first = MatchSpanPos(*_element_ids_itr, 0);
            _last = MatchSpanPos(*_element_ids_itr, std::numeric_limits<uint32_t>::max());
            ++_element_ids_itr;
            while (_element_ids_itr != _element_ids.end() && *_element_ids_itr == _last.element_id() + 1) {
                _last = MatchSpanPos(*_element_ids_itr, std::numeric_limits<uint32_t>::max());
                ++_element_ids_itr;
            }
        }
    }
    [[nodiscard]] bool seek(uint32_t, const MatchSpanPos& seek_to) noexcept {
        while (_valid && _last < seek_to) {
            step();
        }
        return _valid;
    }
    [[nodiscard]] bool is_after(uint32_t, const MatchSpanPos& last_pos) noexcept {
        return _first > last_pos;
    }
    [[nodiscard]] bool valid() const noexcept { return _valid; }
    [[nodiscard]] const MatchSpanPos& first() const noexcept { return _first; }
    [[nodiscard]] const MatchSpanPos& last() const noexcept { return _last; }
};

}

FilteredMatchSpans::FilteredMatchSpans() noexcept
    : _filtered_spans()
{
}

FilteredMatchSpans::~FilteredMatchSpans() = default;

template <typename MatchSpanFilter>
std::span<const MatchSpan>
FilteredMatchSpans::intersection_helper(std::span<const MatchSpan> spans, MatchSpanFilter filter)
{
    _filtered_spans.clear();
    std::optional<uint32_t> field_id;
    for (auto& match_span : spans) {
        if (!field_id.has_value() || field_id.value() != match_span.field_id()) {
            field_id = match_span.field_id();
            filter.new_field();
        }
        if (!filter.seek(match_span.field_id(), match_span.first())) {
            continue;
        }
        while (!filter.is_after(match_span.field_id(), match_span.last())) {
            /*
             * match_span and the filter span overlaps. Calculate first and last match span position of the
             * intersection. Element id has higher priority than position in element when comparing match
             * span positions.
             */
            auto first = std::max(match_span.first(), filter.first());
            auto last = std::min(match_span.last(), filter.last());
            _filtered_spans.emplace_back(match_span.field_id(), first, last);
            if (filter.last() >= match_span.last()) {
                break;
            }
            filter.step();
            if (!filter.valid()) {
                break;
            }
        }
    }
    return _filtered_spans;
}

std::span<const MatchSpan>
FilteredMatchSpans::intersection(std::span<const MatchSpan> spans, ElementIds element_ids)
{
    if (element_ids.all_elements()) {
        return spans;
    }
    if (element_ids.empty() || spans.empty()) {
        return {};
    }
    return intersection_helper(spans, ElementIdMatchSpanFilter({element_ids.begin(), element_ids.end()}));
}

}
