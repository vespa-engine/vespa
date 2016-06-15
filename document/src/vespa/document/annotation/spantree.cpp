// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".spantree");

#include "spantree.h"

#include "annotation.h"
#include "spannode.h"
#include <vespa/vespalib/stllike/string.h>

using std::unique_ptr;
using std::ostream;
using std::ostringstream;
using std::string;
using vespalib::stringref;

namespace document {

SpanTree::~SpanTree() {
}

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

void SpanTree::print(ostream& out, bool verbose, const string& indent) const {
    out << "SpanTree(\"" << _name << "\""
        << "\n" << indent << "  ";
    _root->print(out, verbose, indent + "  ");
    for (const Annotation & a : _annotations) {
        if (a.valid()) {
            out << "\n" << indent << "  ";
            a.print(out, verbose, indent + "  ");
        }
    }
    out << ")";
}

int SpanTree::compare(const SpanTree &other) const {
    ostringstream out_this, out_other;
    print(out_this, true, "");
    other.print(out_other, true, "");
    return stringref(out_this.str()).compare(stringref(out_other.str()));
}

}  // namespace document
