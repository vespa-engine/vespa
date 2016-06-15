// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "orderingspecification.h"
#include <sstream>

namespace document {

bool
OrderingSpecification::operator==(const OrderingSpecification& other) const {
    return _order == other._order && _orderingStart == other._orderingStart && _widthBits == other._widthBits && _divisionBits == other._divisionBits;
}

std::string
OrderingSpecification::toString() const {
    std::ostringstream ost;
    ost << (_order == ASCENDING ? "+" : "-") << "," << _widthBits << "," << _divisionBits << "," << _orderingStart;
    return ost.str();
}

}
