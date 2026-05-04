// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_span.h"

#include <ostream>

namespace search::queryeval {

std::ostream& operator<<(std::ostream& os, const MatchSpanPos& match_span_pos) {
    return os << "{" << match_span_pos.element_id() << "," << match_span_pos.pos() << "}";
}

std::ostream& operator<<(std::ostream& os, const MatchSpan& match_span) {
    return os << "{" << match_span.field_id() << "," << match_span.first() << "," << match_span.last() << "}";
}

} // namespace search::queryeval
