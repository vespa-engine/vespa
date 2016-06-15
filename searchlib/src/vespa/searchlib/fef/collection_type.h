// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {
namespace fef {

/**
 * Typesafe enum used to indicate the collection type of a field.
 **/
class CollectionType
{
private:
    uint32_t _value;

    CollectionType(uint32_t value);
public:
    /**
     * Indicating that the field is single-value
     **/
    static const CollectionType SINGLE;

    /**
     * Indicating that the field is multi-value without element weights
     **/
    static const CollectionType ARRAY;

    /**
     * Indicating that the field is multi-value with element weights
     **/
    static const CollectionType WEIGHTEDSET;

    /**
     * Less than operator; needed to be handled as a value by the standard library.
     **/
    bool operator<(const CollectionType &rhs) const { return (_value < rhs._value); }

    /**
     * Check if two collection types are equal.
     **/
    bool operator==(const CollectionType &rhs) const { return (_value == rhs._value); }

    /**
     * Check if two collection types are not equal.
     **/
    bool operator!=(const CollectionType &rhs) const { return (_value != rhs._value); }
};

} // namespace fef
} // namespace search

