// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result.h"
#include <ostream>

namespace document::select {

Result Result::Invalid;
Result Result::False;
Result Result::True;

Result::Result() = default;

const Result&
Result::operator!() const {
    if (this == &Invalid) {
        return Invalid;
    }
    return (this == &True ? False : True);
}

const Result&
Result::operator&&(const Result& r) const {
    if (this == &False || &r == &False) {
        return False;
    }
    return (this == &True && &r == &True ? True : Invalid);
}

const Result&
Result::operator||(const Result& r) const {
    if (this == &True || &r == &True) {
        return True;
    }
    if (this == &Invalid || &r == &Invalid) {
        return Invalid;
    }
    return False;
}

void
Result::print(std::ostream& out, bool,
              const std::string&) const
{
    if (this == &Invalid) out << "Invalid";
    else if (this == &True) out << "True";
    else out << "False";
}

}
