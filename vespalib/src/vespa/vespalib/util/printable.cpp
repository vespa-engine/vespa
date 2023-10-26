// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "printable.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <sstream>

namespace vespalib {

std::string Printable::toString(bool verbose, const std::string& indent) const
{
    std::ostringstream o;
    print(o, verbose, indent);
    return o.str();
}

vespalib::string
AsciiPrintable::PrintProperties::indent(uint32_t extraLevels) const
{
    vespalib::asciistream as;
    as << _indent;
    for (uint32_t i=0; i<extraLevels; ++i) {
        as << "  ";
    }
    return as.str();
}

void
AsciiPrintable::print(std::ostream& out, bool verbose,
                      const std::string& indent) const
{
    vespalib::asciistream as;
    print(as, PrintProperties(verbose ? VERBOSE : NORMAL, indent));
    out << as.str();
}

vespalib::string
AsciiPrintable::toString(const PrintProperties& p) const
{
    vespalib::asciistream as;
    print(as, p);
    return as.str();
}

std::ostream& operator<<(std::ostream& out, const Printable& p) {
    p.print(out);
    return out;
}

vespalib::asciistream& operator<<(vespalib::asciistream& out, const AsciiPrintable& p)
{
    p.print(out);
    return out;
}

} // vespalib
