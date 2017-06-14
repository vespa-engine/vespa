// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "orderingspecification.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace document {

bool
OrderingSpecification::operator==(const OrderingSpecification& other) const {
    return _order == other._order && _orderingStart == other._orderingStart && _widthBits == other._widthBits && _divisionBits == other._divisionBits;
}

vespalib::string
OrderingSpecification::toString() const {
    vespalib::asciistream ost;
    ost << (_order == ASCENDING ? "+" : "-") << "," << _widthBits << "," << _divisionBits << "," << _orderingStart;
    return ost.str();
}

std::ostream&
operator<<(std::ostream& out, const OrderingSpecification& o)
{
    out << o.toString();
    return out;
}

}
