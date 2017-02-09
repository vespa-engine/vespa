// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitorordering.h"
#include <sstream>

namespace vdslib {

std::string
VisitorOrdering::toString() const {
    std::ostringstream ost;
    ost << (_order == ASCENDING ? "+" : "-") << "," << _widthBits << "," << _divisionBits << "," << _orderingStart;
    return ost.str();
}

}
