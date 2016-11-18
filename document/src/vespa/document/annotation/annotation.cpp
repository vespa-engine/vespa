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
    os << "Annotation(" << *_type;
    if (_value.get()) {
        os << "\n";
        _value->print(os, false, "");
    }
    if (_node) {
        os << "\n";
        os << _node->toString();
    }
    os << ")";
    return os.str();
}

}  // namespace document
