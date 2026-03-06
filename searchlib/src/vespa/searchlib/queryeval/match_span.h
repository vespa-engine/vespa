// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/termfieldmatchdataposition.h>
#include <vespa/searchlib/query/streaming/hit.h>
#include <cstdint>
#include <iosfwd>

namespace search::queryeval {

/*
 * First or last position in a match span.
 */
class MatchSpanPos {
    uint32_t _element_id;
    uint32_t _pos;

public:
    explicit MatchSpanPos(uint32_t element_id_, uint32_t pos_)
        : _element_id(element_id_),
          _pos(pos_)
    {
    }
    explicit MatchSpanPos(const fef::TermFieldMatchDataPosition& tfmd_pos, bool last)
        : _element_id(tfmd_pos.getElementId()),
          _pos(tfmd_pos.getPosition() + (last ? (tfmd_pos.getMatchLength() - 1) : 0))
    {
    }
    explicit MatchSpanPos(const streaming::Hit& hit)
        : _element_id(hit.element_id()),
          _pos(hit.position()) {
    }
    bool operator<(const MatchSpanPos& rhs) const {
        if (_element_id != rhs._element_id) {
            return _element_id < rhs._element_id;
        } else {
            return _pos < rhs._pos;
        }
    }

    uint32_t element_id() const noexcept { return _element_id; }
    uint32_t pos() const noexcept { return _pos; }
    auto operator<=>(const MatchSpanPos& rhs) const = default;
};

/*
 * A match span describes the inclusive range of positions related to a match for the near and onear query operators.
 */
class MatchSpan {
    uint32_t _field_id;
    MatchSpanPos _first;
    MatchSpanPos _last;

public:
    explicit MatchSpan(uint32_t field_id_, const MatchSpanPos& first_, const MatchSpanPos& last_)
        : _field_id(field_id_),
          _first(first_),
          _last(last_)
    {
    }

    explicit MatchSpan(uint32_t field_id_, const fef::TermFieldMatchDataPosition& first_,
                      const fef::TermFieldMatchDataPosition& last_)
        : _field_id(field_id_),
          _first(first_, false),
          _last(last_, true)
    {
    }

    explicit MatchSpan(const streaming::Hit& first_, const streaming::Hit& last_)
        : _field_id(first_.field_id()),
          _first(first_),
          _last(last_)
    {
    }

    void merge_spans(const MatchSpan& rhs) { _last = rhs._last; }

    uint32_t field_id() const noexcept { return _field_id; }
    const MatchSpanPos& first() const noexcept { return _first; }
    const MatchSpanPos& last() const noexcept { return _last; }
    auto operator<=>(const MatchSpan& rhs) const = default;
};

std::ostream& operator<<(std::ostream& os, const MatchSpanPos& match_span_pos);
std::ostream& operator<<(std::ostream& os, const MatchSpan& match_span);

}
