// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hit.h"
#include <ostream>

namespace search::streaming {

std::ostream&
operator<<(std::ostream& os, const Hit& hit)
{
    os << "{" << hit.field_id() << "," << hit.element_id() << "," <<
        hit.element_weight() << "," << hit.element_length() << "," <<
        hit.position() << "}";
    return os;
}

}
