// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".annotation");

#include "annotation.h"
#include "spannode.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/util/bytebuffer.h>

using std::ostream;
using std::string;

namespace document {

Annotation::~Annotation() {
}

void
Annotation::print(ostream& out, bool verbose, const string& indent) const {
    out << "Annotation(" << *_type;
    if (_value.get()) {
        out << "\n" << indent << "  ";
        _value->print(out, verbose, indent + "  ");
    }
    if (_node) {
        out << "\n" << indent << "  ";
        _node->print(out, verbose, indent + "  ");
    }
    out << ")";
}

}  // namespace document
