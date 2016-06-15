// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace tensor {

/*
 * Combine two tensor addresses, but fail if dimension label doesn't match
 * for common dimensions.  Use 3-way merge between two tensors and a vector
 * of dimensions. To be used when we have few common dimensions.
 * The commonDimensions parameter is the intersection of the
 * dimensions in the two input tensors.
 */
template <class AddressBuilder, class LhsAddress, class RhsAddress>
bool
joinTensorAddresses(AddressBuilder &combined,
                    const DimensionsVector &commonDimensions,
                    const LhsAddress &lhs,
                    const RhsAddress &rhs)
{
    TensorAddressElementIterator<LhsAddress> lhsItr(lhs);
    TensorAddressElementIterator<RhsAddress> rhsItr(rhs);
    DimensionsVectorIterator dimsItr(commonDimensions);
    combined.clear();
    while (lhsItr.valid()) {
        while (dimsItr.beforeDimension(lhsItr)) {
            rhsItr.addElements(combined, dimsItr);
            if (rhsItr.atDimension(dimsItr.dimension())) {
                // needed dimension missing from lhs
                return false;
            }
            dimsItr.next();
        }
        if (dimsItr.atDimension(lhsItr.dimension())) {
            rhsItr.addElements(combined, dimsItr);
            if (!rhsItr.atDimension(dimsItr.dimension())) {
                // needed dimension missing from rhs
                return false;
            }
            if (lhsItr.label() != rhsItr.label()) {
                // dimension exists in both rhs and lhs, but labels don't match
                return false;
            }
            // common dimension, labels match
            lhsItr.addElement(combined);
            lhsItr.next();
            rhsItr.next();
            dimsItr.next();
            continue;
        }
        rhsItr.addElements(combined, lhsItr);
        assert(lhsItr.beforeDimension(rhsItr));
        lhsItr.addElement(combined);
        lhsItr.next();
    }
    while (dimsItr.valid()) {
        rhsItr.addElements(combined, dimsItr);
        if (rhsItr.atDimension(dimsItr.dimension())) {
            // needed dimension missing from lhs
            return false;
        }
        dimsItr.next();
    }
    rhsItr.addElements(combined);
    // All matching
    return true;
}

/*
 * Combine two tensor addresses, but fail if dimension label doesn't match
 * for common dimensions.  Use 3-way merge between two tensors and a vector
 * of dimensions. To be used when we have many common dimensions.
 * The commonDimensions parameter is the intersection of the
 * dimensions in the two input tensors.
 */
template <class AddressBuilder, class LhsAddress, class RhsAddress>
bool
joinTensorAddresses(AddressBuilder &combined,
                    const DimensionsSet &commonDimensions,
                    const LhsAddress &lhs,
                    const RhsAddress &rhs)
{
    TensorAddressElementIterator<LhsAddress> lhsItr(lhs);
    TensorAddressElementIterator<RhsAddress> rhsItr(rhs);
    combined.clear();
    if (lhsItr.valid() && rhsItr.valid()) {
        for (;;) {
            if (lhsItr.beforeDimension(rhsItr)) {
                if (!lhsItr.addElements(combined, commonDimensions, rhsItr)) {
                    return false;
                }
                if (!lhsItr.valid()) {
                    break;
                }
            }
            if (lhsItr.dimension() == rhsItr.dimension()) {
                if (lhsItr.label() != rhsItr.label()) {
                    return false;
                }
                lhsItr.addElement(combined);
                lhsItr.next();
                rhsItr.next();
                if (!lhsItr.valid() || !rhsItr.valid()) {
                    break;
                }
                continue;
            }
            if (!rhsItr.addElements(combined, commonDimensions, lhsItr)) {
                return false;
            }
            if (!rhsItr.valid()) {
                break;
            }
        }
    }
    if (!lhsItr.addElements(combined, commonDimensions)) {
        return false;
    }
    if (!rhsItr.addElements(combined, commonDimensions)) {
        return false;
    }
    // All matching
    return true;
}

} // namespace vespalib::tensor
} // namespace vespalib
