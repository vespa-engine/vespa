// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <cstdint>

namespace vdslib {

class VisitorOrdering {
public:
    enum Order { ASCENDING = 0, DESCENDING };

    VisitorOrdering()
        : _order(ASCENDING), _orderingStart(0), _widthBits(0), _divisionBits(0) {};

    VisitorOrdering(Order order)
        : _order(order), _orderingStart(0), _widthBits(0), _divisionBits(0) {};

    VisitorOrdering(Order order, uint64_t orderingStart, uint16_t widthBits, uint16_t divisionBits)
        : _order(order), _orderingStart(orderingStart), _widthBits(widthBits), _divisionBits(divisionBits) {}

    Order getOrder() const { return _order; }
    uint64_t getOrderingStart() const { return _orderingStart; }
    uint16_t getWidthBits() const { return _widthBits; }
    uint16_t getDivisionBits() const { return _divisionBits; }

    std::string toString() const;

private:
    Order _order;
    uint64_t _orderingStart;
    uint16_t _widthBits;
    uint16_t _divisionBits;
};

}

