// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document {
class AlternateSpanList;
class Span;
class SpanList;
class SimpleSpanList;

struct SpanTreeVisitor {
    virtual ~SpanTreeVisitor() {}

    virtual void visit(const Span &) = 0;
    virtual void visit(const SpanList &node) = 0;
    virtual void visit(const SimpleSpanList &node) = 0;
    virtual void visit(const AlternateSpanList &node) = 0;
};

}  // namespace document

