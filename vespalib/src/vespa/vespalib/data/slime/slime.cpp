// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slime.h"

namespace vespalib {

Slime::~Slime() { }

bool operator == (const Slime & a, const Slime & b)
{
    return a.get() == b.get();
}

std::ostream & operator << (std::ostream & os, const Slime & slime)
{
    os << slime.toString();
    return os;
}

} // namespace vespalib
