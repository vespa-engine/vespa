// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace storage {

namespace distributor {

struct VisitorOrder {
    const document::OrderingSpecification& _ordering;

    VisitorOrder(const document::OrderingSpecification& ordering)
        : _ordering(ordering) {}

    document::BucketId::Type getOrder(const document::BucketId& bid) {
        int32_t orderBitCount = _ordering.getWidthBits() -
                                _ordering.getDivisionBits();
        document::BucketId::Type order = bid.withoutCountBits();
        order >>= 32;
        order <<= 64 - orderBitCount;
        order = document::BucketId::reverse(order);
        return order;
    }

    document::BucketId::Type padOnesRight(const document::BucketId::Type& id,
            int32_t count) {
        document::BucketId::Type res = id;
        document::BucketId::Type one = 1;
        for (int32_t i=0; i<count; i++) {
            res |= (one << i);
        }
        return res;
    }

    bool operator()(const document::BucketId& a, const document::BucketId& b) {
        if (a == document::BucketId(INT_MAX) ||
            b == document::BucketId(0, 0)) {
            return false; // All before max, non before null
        }
        if (a == document::BucketId(0, 0) ||
            b == document::BucketId(INT_MAX)) {
            return true; // All after null, non after max
        }
        int32_t orderBitCount = _ordering.getWidthBits() -
                                _ordering.getDivisionBits();
        int32_t aOrderBitsUsed = std::max((int32_t)a.getUsedBits() - 32, 0);
        int32_t bOrderBitsUsed = std::max((int32_t)b.getUsedBits() - 32, 0);
        if (orderBitCount <= 0 ||
            aOrderBitsUsed == 0 ||
            bOrderBitsUsed == 0) {
            return (a.toKey() < b.toKey()); // Reversed bucket id order
        }

        document::BucketId::Type aOrder = getOrder(a);
        document::BucketId::Type bOrder = getOrder(b);

        document::BucketId::Type sOrder = _ordering.getOrderingStart();
        sOrder <<= 64 - _ordering.getWidthBits();
        sOrder >>= 64 - orderBitCount;

        if (_ordering.getOrder() == document::OrderingSpecification::ASCENDING) {
            aOrder = padOnesRight(aOrder, orderBitCount - aOrderBitsUsed);
            bOrder = padOnesRight(bOrder, orderBitCount - bOrderBitsUsed);
        }

        aOrder -= sOrder;
        bOrder -= sOrder;

        if (_ordering.getOrder() == document::OrderingSpecification::DESCENDING) {
            aOrder = -aOrder;
            bOrder = -bOrder;
        }

        if (aOrder == bOrder) {
            return (a.toKey() < b.toKey()); // Reversed bucket id order
        }
        return (aOrder < bOrder);
    }
};

}

}


