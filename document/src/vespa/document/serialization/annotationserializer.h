// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/annotation/spantreevisitor.h>
#include <map>
#include <cstddef>

namespace vespalib { class nbostream; }

namespace document {
class AlternateSpanList;
class Annotation;
class Span;
class SpanList;
class SimpleSpanList;
struct SpanNode;
class SpanTree;

class AnnotationSerializer : private SpanTreeVisitor {
    vespalib::nbostream &_stream;
    std::map<const SpanNode *, size_t> _span_node_map;

    void visit(const Span &node) override { writeSpan(node); }
    void visit(const SpanList &node) override { writeList(node); }
    void visit(const SimpleSpanList &node) override { writeList(node); }
    void visit(const AlternateSpanList &node) override { writeList(node); }

public:
    AnnotationSerializer(vespalib::nbostream &stream);

    void write(const SpanTree &tree);
    void write(const SpanNode &node);
    void write(const Annotation &annotation);
    void writeSpan(const Span &node);
    void writeList(const SpanList &list);
    void writeList(const SimpleSpanList &list);
    void writeList(const AlternateSpanList &list);
};

}  // namespace document

