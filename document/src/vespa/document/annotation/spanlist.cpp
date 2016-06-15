// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".spanlist");

#include "spanlist.h"

using std::ostream;
using std::string;

namespace document {

SpanList::~SpanList() {
    for (size_t i = 0; i < _span_vector.size(); ++i) {
        delete _span_vector[i];
    }
}

void SpanList::print(ostream& out, bool verbose, const string& indent) const {
    out << "SpanList(";
    if (_span_vector.size() > 1) {
        out << "\n" << indent << "  ";
    }
    for (size_t i = 0; i < _span_vector.size(); ++i) {
        _span_vector[i]->print(out, verbose, indent + "  ");
        if (i < _span_vector.size() - 1) {
            out << "\n" << indent << "  ";
        }
    }
    out << ")";
}

SimpleSpanList::SimpleSpanList(size_t sz) :
    _span_vector(sz)
{
}

SimpleSpanList::~SimpleSpanList()
{
}

void SimpleSpanList::print(ostream& out, bool verbose, const string& indent) const {
    out << "SimpleSpanList(";
    if (_span_vector.size() > 1) {
        out << "\n" << indent << "  ";
    }
    for (size_t i = 0; i < _span_vector.size(); ++i) {
        _span_vector[i].print(out, verbose, indent + "  ");
        if (i < _span_vector.size() - 1) {
            out << "\n" << indent << "  ";
        }
    }
    out << ")";
}

}  // namespace document
