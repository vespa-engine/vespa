// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/annotation/span.h>
#include <vespa/document/serialization/util.h>
#include <vector>

namespace vespalib {
    class nbostream;
}

namespace document {
class AlternateSpanList;
class Annotation;
class FixedTypeRepo;
class SpanList;
class SpanTree;
class SimpleSpanList;

class AnnotationDeserializer {
public:
    AnnotationDeserializer(const FixedTypeRepo &repo, vespalib::nbostream &stream, uint16_t version);

    std::unique_ptr<SpanTree> readSpanTree();
    std::unique_ptr<SpanNode> readSpanNode();
    // returns 0 if the annotation type is unknown.
    std::unique_ptr<AlternateSpanList> readAlternateSpanList();
    void readAnnotation(Annotation & annotation);
private:
    std::unique_ptr<SpanList> readSpanList();
    std::unique_ptr<SimpleSpanList> readSimpleSpanList();
    void readSpan(Span & span) {
        span.from(getInt1_2_4Bytes(_stream));
        span.length(getInt1_2_4Bytes(_stream));
    }

    const FixedTypeRepo    &_repo;
    vespalib::nbostream    &_stream;
    uint16_t                _version;
    std::vector<SpanNode *> _nodes;
};
}  // namespace document

