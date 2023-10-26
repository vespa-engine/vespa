// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "span.h"
#include <memory>
#include <vector>

namespace document {

class SpanList : public SpanNode {
    std::vector<SpanNode *> _span_vector;

public:
    using UP = std::unique_ptr<SpanList>;
    using const_iterator = std::vector<SpanNode *>::const_iterator;

    ~SpanList();

    template <typename T>
    T &add(std::unique_ptr<T> node) {
        T *n = node.get();
        _span_vector.push_back(node.release());
        return *n;
    }

    size_t size() const { return _span_vector.size(); }
    void reserve(size_t sz) { _span_vector.reserve(sz); }

    const_iterator begin() const { return _span_vector.begin(); }
    const_iterator end() const { return _span_vector.end(); }

    void accept(SpanTreeVisitor &visitor) const override;
};

class SimpleSpanList : public SpanNode {
    using SpanVector = std::vector<Span>;
    SpanVector _span_vector;

public:
    using UP = std::unique_ptr<SimpleSpanList>;
    using const_iterator = SpanVector::const_iterator;

    SimpleSpanList(size_t sz);
    ~SimpleSpanList();

    size_t size() const { return _span_vector.size(); }
    Span & operator [] (size_t index) { return _span_vector[index]; }
    const Span & operator [] (size_t index) const { return _span_vector[index]; }

    const_iterator begin() const { return _span_vector.begin(); }
    const_iterator end() const { return _span_vector.end(); }

    void accept(SpanTreeVisitor &visitor) const override;
};

}  // namespace document

