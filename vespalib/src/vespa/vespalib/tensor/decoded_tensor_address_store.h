// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace tensor {

/**
 * A utility class to store decoded tensor address based on data stored
 * in tensors.
 */
template <class AddressT> class DecodedTensorAddressStore;

/**
 * A utility class to store decoded tensor address.  TensorAddress
 * doesn't need any decoding, just pass through the argument
 * (e.g. tensor address in tensor hash table).
 */
template <> class DecodedTensorAddressStore<TensorAddress>
{
public:
    void set(const TensorAddress &) { }
    static const TensorAddress &get(const TensorAddress &rhs) { return rhs; }
};

/**
 * A utility class to store decoded tensor address.
 * CompactTensorAddress needs decoding.
 */
template <> class DecodedTensorAddressStore<CompactTensorAddress>
{
private:
    CompactTensorAddress _address;
public:
    void set(const CompactTensorAddressRef rhs)
    { _address.deserializeFromSparseAddressRef(rhs); }
    const CompactTensorAddress &get(const CompactTensorAddressRef &)
    { return _address; }
};

/**
 * A utility class to store decoded tensor address.  Just pass through
 * the argument (e.g. tensor address ref in tensor hash table).
 * CompactTensorAddressRef is encoded, decoding is performed on the
 * fly while iterating.
 */
template <> class DecodedTensorAddressStore<CompactTensorAddressRef>
{
public:
    void set(const CompactTensorAddressRef &) { }
    static CompactTensorAddressRef get(const CompactTensorAddressRef rhs)
    { return rhs; }
};


} // namespace vespalib::tensor
} // namespace vespalib
