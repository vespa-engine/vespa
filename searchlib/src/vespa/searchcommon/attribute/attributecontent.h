// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributevector.h"
#include <cstdint>

namespace search::attribute {


/**
 * This class is wrapping an array of type T and is used to hold the
 * attribute vector content for a given document. The values stored for the
 * given document in the attribute vector is copied into the array wrapped
 * in an instance of this class.
 *
 * @param T the type of the data stored in this object
 **/
template <typename T>
class AttributeContent
{
private:
    T _staticBuf[16];
    T * _dynamicBuf;
    uint32_t _size;
    uint32_t _capacity;

    AttributeContent(const AttributeContent & rhs);
    AttributeContent & operator=(const AttributeContent & rhs);

public:
    /**
     * Creates a new object with an initial capacity of 16 without dynamic allocation.
     **/
    AttributeContent() :
        _dynamicBuf(nullptr),
        _size(0),
        _capacity(16)
    {
    }
    /**
     * Destructs the object.
     **/
    ~AttributeContent() {
        if (_dynamicBuf != nullptr) {
            delete [] _dynamicBuf;
        }
    }

    /**
     * Returns a read-only iterator to the beginning of the underlying data array.
     *
     * @return iterator
     **/
    const T * begin() const {
        if (_dynamicBuf != nullptr) {
            return _dynamicBuf;
        }
        return _staticBuf;
    }

    /**
     * Returns a read-only iterator to the end of the underlying data array.
     *
     * @return iterator
     **/
    const T * end() const {
        return begin() + _size;
    }

    /**
     * Returns the element at the given position in the underlying data array.
     *
     * @return read-only reference to the element
     * @param idx position into the underlying data
     **/
    const T & operator[](uint32_t idx) const {
        return *(begin() + idx);
    }

    /**
     * Returns the number of elements used in the underlying data array.
     *
     * @return number of elements used
     **/
    uint32_t size() const {
        return _size;
    }

    /**
     * Returns the number of elements allocated in the underlying data array.
     *
     * @return number of elements allocated
     **/
    uint32_t capacity() const {
        return _capacity;
    }

    /**
     * Returns a read/write pointer to the underlying data array.
     *
     * @return read/write pointer.
     **/
    T * data() {
        if (_dynamicBuf != nullptr) {
            return _dynamicBuf;
        }
        return _staticBuf;
    }

    /**
     * Sets the number of elements used in the underlying data array.
     *
     * @param n number of elements used
     **/
    void setSize(uint32_t n) {
        _size = n;
    }

    /**
     * Allocates memory so that the underlying data array can hold the
     * given number of elements (capacity) and sets the size to 0.
     * A new data array will only be allocated if n > capacity().
     *
     * @param n wanted number of elements
     **/
    void allocate(uint32_t n) {
        if (n > _capacity) {
            if (_dynamicBuf != nullptr) {
                delete [] _dynamicBuf;
            }
            _dynamicBuf = new T[n];
            _capacity = n;
            _size = 0;
        }
    }

    /**
     * Fill this buffer with the content of the given attribute vector for the given docId.
     *
     * @param attribute the attribute vector
     * @param docId the docId
     **/
    void fill(const IAttributeVector & attribute, IAttributeVector::DocId docId)
    {
        uint32_t count = attribute.get(docId, data(), capacity());
        while (count > capacity()) {
            allocate(count);
            count = attribute.get(docId, data(), capacity());
        }
        setSize(count);
    }
};

typedef AttributeContent<double>       FloatContent;
typedef AttributeContent<const char *> ConstCharContent;
typedef AttributeContent<IAttributeVector::largeint_t> IntegerContent;
typedef AttributeContent<IAttributeVector::EnumHandle> EnumContent;
typedef AttributeContent<IAttributeVector::WeightedInt>       WeightedIntegerContent;
typedef AttributeContent<IAttributeVector::WeightedFloat>     WeightedFloatContent;
typedef AttributeContent<IAttributeVector::WeightedConstChar> WeightedConstCharContent;
typedef AttributeContent<IAttributeVector::WeightedString>    WeightedStringContent;
typedef AttributeContent<IAttributeVector::WeightedEnum>      WeightedEnumContent;
typedef IAttributeVector::EnumHandle EnumHandle;

}
