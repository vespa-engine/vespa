// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastos/types.h>
#include <vespa/vespalib/stllike/string.h>

namespace document {

class OrderingSpecification {
public:
    typedef std::unique_ptr<OrderingSpecification> UP;

    enum Order { ASCENDING = 0, DESCENDING };

    OrderingSpecification()
        : _order(ASCENDING), _orderingStart(0), _widthBits(0), _divisionBits(0) {};

    OrderingSpecification(Order order)
        : _order(order), _orderingStart(0), _widthBits(0), _divisionBits(0) {};

    OrderingSpecification(Order order, uint64_t orderingStart, uint16_t widthBits, uint16_t divisionBits)
        : _order(order), _orderingStart(orderingStart), _widthBits(widthBits), _divisionBits(divisionBits) {}

    Order getOrder() const { return _order; }
    uint64_t getOrderingStart() const { return _orderingStart; }
    uint16_t getWidthBits() const { return _widthBits; }
    uint16_t getDivisionBits() const { return _divisionBits; }

    bool operator==(const OrderingSpecification& other) const;

    vespalib::string toString() const;

private:
    Order _order;
    uint64_t _orderingStart;
    uint16_t _widthBits;
    uint16_t _divisionBits;
};

std::ostream&
operator<<(std::ostream& out, const OrderingSpecification& o);

}
