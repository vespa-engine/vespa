// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {
namespace fef {

/**
 * Typesafe enum used to indicate the type of a field.
 **/
class FieldType
{
private:
    uint32_t _value;

    FieldType(uint32_t value);
public:
    /**
     * Indicating that the field is indexed
     **/
    static const FieldType INDEX;

    /**
     * Indicating that the field is kept in an attribute vector
     **/
    static const FieldType ATTRIBUTE;

    /**
     * Indicating that the field is kept in an attribute vector
     **/
    static const FieldType HIDDEN_ATTRIBUTE;

    /**
     * Less than operator; needed to be handled as a value by the standard library.
     **/
    bool operator<(const FieldType &rhs) const { return (_value < rhs._value); }

    /**
     * Check if two field types are equal.
     **/
    bool operator==(const FieldType &rhs) const { return (_value == rhs._value); }

    /**
     * Check if two field types are not equal.
     **/
    bool operator!=(const FieldType &rhs) const { return (_value != rhs._value); }
};

} // namespace fef
} // namespace search

