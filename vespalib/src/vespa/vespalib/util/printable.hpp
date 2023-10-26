// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "printable.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

template<typename T>
void print(const std::vector<T> & v, vespalib::asciistream& out, const AsciiPrintable::PrintProperties& p) {
    if (v.empty()) {
        out << "[]";
        return;
    }
    vespalib::asciistream ost;
    ost << v[0];
    bool newLineBetweenEntries = (ost.str().size() > 15);
    out << "[";
    for (size_t i=0; i<v.size(); ++i) {
        if (i != 0) out << ",";
        if (newLineBetweenEntries) {
            out << "\n" << p.indent(1);
        } else {
            if (i != 0) { out << " "; }
        }
        out << v[i];
    }
    if (newLineBetweenEntries) {
        out << "\n" << p.indent();
    }
    out << "]";
}

} // vespalib
