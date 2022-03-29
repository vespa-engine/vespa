// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "spantree.h"
#include "spannode.h"
#include <vespa/vespalib/stllike/asciistream.h>

using std::unique_ptr;
using vespalib::stringref;

namespace document {

SpanTree::~SpanTree() = default;

size_t
SpanTree::annotate(std::unique_ptr<Annotation> annotation_) {
    _annotations.push_back(std::move(*annotation_));
    return _annotations.size() - 1;
}

size_t
SpanTree::annotate(const SpanNode &node, std::unique_ptr<Annotation> annotation_) {
    annotation_->setSpanNode(node);
    return annotate(std::move(annotation_));
}

size_t
SpanTree::annotate(const SpanNode &node, const AnnotationType &type) {
    return annotate(node, std::make_unique<Annotation>(type));
}

void
SpanTree::accept(SpanTreeVisitor &visitor) const {
    _root->accept(visitor);
}

int
SpanTree::compare(const SpanTree &other) const {
    return toString().compare(other.toString());
}

vespalib::string
SpanTree::toString() const {
    vespalib::asciistream os;
    os << "SpanTree(\"" << _name << "\"" << "\n  ";
    os <<_root->toString();
    for (const Annotation & a : _annotations) {
        if (a.valid()) {
            os << "\n  " << a.toString();
        }
    }
    os << ")";
    return os.str();
}


}  // namespace document
