// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "spantree.h"
#include "annotation.h"
#include "spannode.h"
#include <vespa/vespalib/stllike/string.h>

using std::unique_ptr;
using vespalib::stringref;

namespace document {

SpanTree::~SpanTree() { }

size_t SpanTree::annotate(Annotation::UP annotation_) {
    _annotations.push_back(*annotation_);
    return _annotations.size() - 1;
}

size_t SpanTree::annotate(const SpanNode &node, Annotation::UP annotation_) {
    annotation_->setSpanNode(node);
    return annotate(std::move(annotation_));
}

size_t SpanTree::annotate(const SpanNode &node, const AnnotationType &type) {
    return annotate(node, Annotation::UP(new Annotation(type)));
}

void SpanTree::accept(SpanTreeVisitor &visitor) const {
    _root->accept(visitor);
}

int SpanTree::compare(const SpanTree &other) const {
    //TODO fixme
    (void) other;
    vespalib::string out_this, out_other;
    return out_this.compare(out_other);
}

}  // namespace document
