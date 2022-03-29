// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotation.h"
#include "spannode.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace document {

std::ostream & operator << (std::ostream & os, const Annotation &annotation) {
    return os << annotation.toString();
}

Annotation::~Annotation() = default;

vespalib::string
Annotation::toString() const {
    vespalib::asciistream os;
    os << "Annotation(" << *_type;
    if (_value.get()) {
        os << "\n";
        os << _value->toString();
    }
    if (_node) {
        os << "\n";
        os << _node->toString();
    }
    os << ")";
    return os.str();
}

bool
Annotation::operator==(const Annotation &a2) const {
    return (getType() == a2.getType() &&
            !(!!getFieldValue() ^ !!a2.getFieldValue()) &&
            (!getFieldValue() || (*getFieldValue() == *a2.getFieldValue()))
           );
}

}  // namespace document
