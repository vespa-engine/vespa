// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class vespalib::FloatingPointType
 * \ingroup object
 *
 * \brief Wrapper class for floating point types taking care of comparisons.
 *
 * Due to floating point values not being able to represent a lot of numbers
 * exactly, one should always compare floating point values, allowing slight
 * variations.
 *
 * To avoid having to handle this everywhere in the code, some wrapper classes
 * exist here to take care of this for you. They will automatically convert
 * primitive values to the wrapper class, so you can still use primitive
 * constants in your code.
 *
 * Node that epsilon is currently just set to 10^(-6). We can reduce it or
 * adjust it based on type or value later if we see the need. But the class
 * should define it, at least by default, to make the interface easy to use as
 * most use cases don't really care that much.
 */

#pragma once

#include <iosfwd> // To get std::ostream for output operator

namespace vespalib {

class asciistream;

template<typename Number>
class FloatingPointType {
    Number _value;

public:
    typedef FloatingPointType<Number> Type;

    FloatingPointType() : _value(0.0) {}
    FloatingPointType(Number n) : _value(n) {}

    Number getValue() const { return _value; }

    Number abs() const { return (_value < 0 ? -1 * _value : _value); }

    bool operator==(Type n) const { return ((*this - n).abs() < 0.000001); }
    bool operator!=(Type n) const { return ((*this - n).abs() > 0.000001); }
    bool operator<(Type n) const { return (n._value - 0.000001 > _value); }
    bool operator>(Type n) const { return (n._value + 0.000001 < _value); }
    bool operator<=(Type n) const { return (n._value + 0.000001 > _value); }
    bool operator>=(Type n) const { return (n._value - 0.000001 < _value); }

    Type operator-(Type n) const { return Type(_value - n._value); }
    Type operator+(Type n) const { return Type(_value + n._value); }
    Type operator*(Type n) const { return Type(_value * n._value); }
    Type operator/(Type n) const { return Type(_value / n._value); }

    Type& operator+=(Type n) { _value += n._value; return *this; }
    Type& operator-=(Type n) { _value -= n._value; return *this; }
    Type& operator*=(Type n) { _value *= n._value; return *this; }
    Type& operator/=(Type n) { _value /= n._value; return *this; }

    Type& operator++() { ++_value; return *this; }
    Type operator++(int) { Type t(_value); ++_value; return t; }
    Type& operator--() { --_value; return *this; }
    Type operator--(int) { Type t(_value); --_value; return t; }
};

typedef FloatingPointType<double> Double;
typedef FloatingPointType<double> Float;

template<typename Number>
std::ostream& operator<<(std::ostream& out, FloatingPointType<Number> number);

template<typename Number>
vespalib::asciistream & operator<<(vespalib::asciistream & out, FloatingPointType<Number> number);

} // vespalib

