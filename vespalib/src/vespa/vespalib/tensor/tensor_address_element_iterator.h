// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace tensor {

using DimensionsSet = vespalib::hash_set<vespalib::stringref>;

/**
 * An iterator for tensor address elements used to simplify 3-way merge
 * between two tensor addresses and a dimension vector.
 */
template <class Address>
class TensorAddressElementIterator {
    using InnerIterator = typename Address::Elements::const_iterator;
    InnerIterator _itr;
    InnerIterator _itrEnd;
public:
    TensorAddressElementIterator(const Address &address)
        : _itr(address.elements().cbegin()),
          _itrEnd(address.elements().cend())
    {
    }
    bool valid() const { return (_itr != _itrEnd); }
    vespalib::stringref dimension() const { return _itr->dimension(); }
    vespalib::stringref label() const { return _itr->label(); }
    template <class Iterator>
    bool beforeDimension(const Iterator &rhs) const {
        if (!valid()) {
            return false;
        }
        if (!rhs.valid()) {
            return true;
        }
        return (_itr->dimension() < rhs.dimension());
    }
    bool atDimension(vespalib::stringref rhsDimension) const
    {
        return (valid() && (_itr->dimension() == rhsDimension));
    }
    void next() { ++_itr; }
    template <class AddressBuilder>
    void
    addElement(AddressBuilder &builder) {
        builder.add(_itr->dimension(), _itr->label());
    }
    template <class AddressBuilder, class Iterator>
    void addElements(AddressBuilder &builder, const Iterator &limit)
    {
        while (beforeDimension(limit)) {
            addElement(builder);
            next();
        }
    }
    template <class AddressBuilder, class Iterator>
    bool addElements(AddressBuilder &builder, const DimensionsSet &dims,
                     const Iterator &limit)
    {
        do {
            if (dims.find(_itr->dimension()) != dims.end()) {
                return false;
            }
            addElement(builder);
            next();
        } while (beforeDimension(limit));
        return true;
    }
    template <class AddressBuilder>
    void addElements(AddressBuilder &builder)
    {
        while (valid()) {
            addElement(builder);
            next();
        }
    }
    template <class AddressBuilder>
    bool addElements(AddressBuilder &builder, const DimensionsSet &dims)
    {
        while (valid()) {
            if (dims.find(_itr->dimension()) != dims.end()) {
                return false;
            }
            addElement(builder);
            next();
        }
        return true;
    }

    bool skipToDimension(vespalib::stringref rhsDimension) {
        for (;;) {
            if (!valid()) {
                return false;
            }
            if (dimension() < rhsDimension) {
                next();
            } else {
                return (dimension() == rhsDimension);
            }
        }
    }
};


/**
 * An iterator for tensor address elements used to simplify 3-way merge
 * between two tensor addresses and a dimension vector.
 * This is a specialization to perform decoding on the fly while iterating.
 */
template <>
class TensorAddressElementIterator<CompactTensorAddressRef> {
    const char *_itr;
    const char *_itrEnd;
    vespalib::stringref _dimension;
    vespalib::stringref _label;

    size_t
    simple_strlen(const char *str) {
        const char *strend = str;
        for (; *strend != '\0'; ++strend) {
        }
        return (strend - str);
    }

    void decodeElement()
    {
        _dimension = vespalib::stringref(_itr, simple_strlen(_itr));
        const char *labelp = _dimension.c_str() + _dimension.size() + 1;
        _label = vespalib::stringref(labelp, simple_strlen(labelp));
        _itr = _label.c_str() + _label.size() + 1;
    }
public:
    TensorAddressElementIterator(CompactTensorAddressRef address)
        : _itr(static_cast<const char *>(address.start())),
          _itrEnd(_itr + address.size()),
          _dimension(),
          _label()
    {
        if (_itr != _itrEnd) {
            decodeElement();
        }
    }
    bool valid() const { return (_dimension.size() != 0u); }
    vespalib::stringref dimension() const { return _dimension; }
    vespalib::stringref label() const { return _label; }
    template <class Iterator>
    bool beforeDimension(const Iterator &rhs) const {
        if (!valid()) {
            return false;
        }
        if (!rhs.valid()) {
            return true;
        }
        return (_dimension < rhs.dimension());
    }
    bool atDimension(vespalib::stringref rhsDimension) const
    {
        return (_dimension == rhsDimension);
    }
    void next() {
        if (_itr != _itrEnd) {
            decodeElement();
        } else {
            _dimension = vespalib::stringref();
            _label = vespalib::stringref();
        }
    }
    template <class AddressBuilder>
    void
    addElement(AddressBuilder &builder) {
        builder.add(_dimension, _label);
    }
    template <class AddressBuilder, class Iterator>
    void addElements(AddressBuilder &builder, const Iterator &limit)
    {
        while (beforeDimension(limit)) {
            addElement(builder);
            next();
        }
    }
    template <class AddressBuilder, class Iterator>
    bool addElements(AddressBuilder &builder, const DimensionsSet &dims,
                     const Iterator &limit)
    {
        do {
            if (dims.find(_dimension) != dims.end()) {
                return false;
            }
            addElement(builder);
            next();
        } while (beforeDimension(limit));
        return true;
    }
    template <class AddressBuilder>
    void addElements(AddressBuilder &builder)
    {
        while (valid()) {
            addElement(builder);
            next();
        }
    }
    template <class AddressBuilder>
    bool addElements(AddressBuilder &builder, const DimensionsSet &dims)
    {
        while (valid()) {
            if (dims.find(_dimension) != dims.end()) {
                return false;
            }
            addElement(builder);
            next();
        }
        return true;
    }
};


} // namespace vespalib::tensor
} // namespace vespalib
