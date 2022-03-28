// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "printable.h"
#include <sstream>

namespace document {

std::string Printable::toString(bool verbose, const std::string& indent) const
{
    std::ostringstream o;
    print(o, verbose, indent);
    return o.str();
}

void Printable::print(std::ostream& out) const {
    print(out, false, "");
}
void Printable::print(std::ostream& out, bool verbose) const {
    print(out, verbose, "");
}
void Printable::print(std::ostream& out, const std::string& indent) const {
    print(out, false, indent);
}

std::ostream& operator<<(std::ostream& out, const Printable& p) {
    p.print(out, false, "");
    return out;
}

}
