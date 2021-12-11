// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "spannode.h"
#include <memory>

namespace document {

class Span : public SpanNode {
    int32_t _from;
    int32_t _length;

public:
    typedef std::unique_ptr<Span> UP;

    Span(int32_t from_pos=0, int32_t len=0) noexcept : _from(from_pos), _length(len) {}

    int32_t from() const { return _from; }
    int32_t length() const { return _length; }
    Span & from(int32_t from_pos) { _from = from_pos; return *this; }
    Span & length(int32_t length_pos) { _length = length_pos; return *this; }

    void accept(SpanTreeVisitor &visitor) const override;
};

inline bool operator==(const Span &span1, const Span &span2) noexcept {
    return span1.from() == span2.from() && span1.length() == span2.length();
}

inline bool operator<(const Span &span1, const Span &span2) noexcept {
    if (span1.from() != span2.from()) {
        return span1.from() < span2.from();
    } else {
        return span1.length() < span2.length();
    }
}

inline bool operator>(const Span &span1, const Span &span2) {
    return span2 < span1;
}

}  // namespace document

