// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "annotation.h"
#include "spannode.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/util/bytebuffer.h>

namespace document {

std::ostream & operator << (std::ostream & os, const Annotation &annotation) {
    return os << annotation.toString();
}

Annotation::~Annotation() { }

vespalib::string
Annotation::toString() const {
    std::ostringstream os;
    vespalib::string indent("");
    os << "Annotation(" << *_type;
    if (_value.get()) {
        os << "\n" << indent << "  ";
        _value->print(os, false, indent + "  ");
    }
    if (_node) {
        os << "\n" << indent << "  ";
        _node->toString();
    }
    os << ")";
    return os.str();
}

}  // namespace document
