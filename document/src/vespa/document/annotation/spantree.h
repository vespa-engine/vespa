// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "annotation.h"
#include <vector>

namespace document {
struct SpanNode;
struct SpanTreeVisitor;

class SpanTree {
    using AnnotationVector = std::vector<Annotation>;
    vespalib::string _name;
    std::unique_ptr<SpanNode> _root;
    std::vector<Annotation> _annotations;

public:
    using UP = std::unique_ptr<SpanTree>;
    using const_iterator = AnnotationVector::const_iterator;

    template <typename T>
    SpanTree(vespalib::stringref name, std::unique_ptr<T> root)
        : _name(name),
          _root(std::move(root)) {
    }
    ~SpanTree();

    // The annotate functions return the annotation index.
    size_t annotate(Annotation&& annotation_);
    size_t annotate(const SpanNode& node, Annotation&& annotation_);
    size_t annotate(const SpanNode& node, const AnnotationType& annotation_type);

    Annotation & annotation(size_t index) { return _annotations[index]; }
    const Annotation & annotation(size_t index) const { return _annotations[index]; }

    void accept(SpanTreeVisitor &visitor) const;

    const vespalib::string & getName() const { return _name; }
    const SpanNode &getRoot() const { return *_root; }
    size_t numAnnotations() const { return _annotations.size(); }
    void reserveAnnotations(size_t sz) { _annotations.resize(sz); }
    const_iterator begin() const { return _annotations.begin(); }
    const_iterator end() const { return _annotations.end(); }
    int compare(const SpanTree &other) const;
    vespalib::string toString() const;
};

}  // namespace document

