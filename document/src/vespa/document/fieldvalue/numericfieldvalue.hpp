// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/lexical_cast.h>
#include <vespa/vespalib/util/exceptions.h>
#include <charconv>

namespace document {

template<typename Number>
FieldValue&
NumericFieldValue<Number>::assign(const FieldValue& value)
{
    if (value.isA(FieldValue::Type::BYTE)) {
        _value = static_cast<Number>(value.getAsByte());
    } else if (value.isA(FieldValue::Type::SHORT)) {
        _value = static_cast<Number>(value.getAsInt());
    } else if (value.isA(FieldValue::Type::INT)) {
        _value = static_cast<Number>(value.getAsInt());
    } else if (value.isA(FieldValue::Type::LONG)) {
        _value = static_cast<Number>(value.getAsLong());
    } else if (value.isA(FieldValue::Type::FLOAT)) {
        _value = static_cast<Number>(value.getAsFloat());
    } else if (value.isA(FieldValue::Type::DOUBLE)) {
        _value = static_cast<Number>(value.getAsDouble());
    } else {
        return FieldValue::assign(value);
    }
    return *this;
}

template<typename Number>
int
NumericFieldValue<Number>::compare( const FieldValue& other) const
{
    int diff = FieldValue::compare(other);
    if (diff != 0) return diff;

    const NumericFieldValue & otherNumber(static_cast<const NumericFieldValue &>(other));
    return  (_value == otherNumber._value)
                ? 0
                : (_value - otherNumber._value > 0)
                    ? 1
                    : -1;
}

template<typename Number>
int
NumericFieldValue<Number>::fastCompare( const FieldValue& other) const
{

    const NumericFieldValue & otherNumber(static_cast<const NumericFieldValue &>(other));
    return  (_value == otherNumber._value)
            ? 0
            : (_value - otherNumber._value > 0)
              ? 1
              : -1;
}

template<typename Number>
void
NumericFieldValue<Number>::print(std::ostream& out, bool, const std::string&) const
{
    if (sizeof(Number) == 1) { // Make sure char's are printed as numbers
        out << (int) _value;
    } else {
        out << _value;
    }
}

template<typename Number>
FieldValue&
NumericFieldValue<Number>::operator=(std::string_view value)
{
    const char *lastp = value.data() + value.size();

    // Lexical cast doesn't allow hex syntax we use in XML,
    // so detect these in front.
    if ((value.size() > 2) && (value[0] == '0') && ((value[1] | 0x20) == 'x')) {
        // It is safe to assume that all hex numbers can be contained within
        // 64 bit unsigned value.
        uint64_t val = vespalib::lexical_cast<uint64_t>({value.data() + 2, lastp}, 16);
        // Allow numbers to be specified in range max signed to max
        // unsigned. These become negative numbers.
        _value = static_cast<Number>(val);
        return *this;
    } else if constexpr (std::is_integral_v<Number>) {
        if (sizeof(Number) == sizeof(int8_t)) {
            int val = vespalib::lexical_cast<int>(value);
            if (val < -128 || val > 255) {
                throw vespalib::IllegalArgumentException(
                        "Value of byte must be in the range -128 to 255", VESPA_STRLOC);
            }
            _value = static_cast<Number>(val);
            return *this;
        } else {
            if constexpr(std::is_signed_v<Number>) {
                // handle unsigned input first:
                using TMP = std::make_unsigned<Number>::type;
                TMP tmp;
                // use from_chars to allow fall through for result out of range
                auto res = std::from_chars(value.data(), lastp, tmp);
                if (res.ec == std::errc{} && res.ptr == lastp) {
                    // Allow numbers to be specified in range max signed to max
                    // unsigned. These become negative numbers.
                    _value = static_cast<Number>(tmp);
                    return *this;
                }
            }
            // fallthrough
        }
    }
    _value = vespalib::lexical_cast<Number>(value);
    return *this;
}

template<typename Number>
char
NumericFieldValue<Number>::getAsByte() const
{
    return static_cast<char>(_value);
}

template<typename Number>
int32_t
NumericFieldValue<Number>::getAsInt() const
{
    return static_cast<int32_t>(_value);
}

template<typename Number>
int64_t
NumericFieldValue<Number>::getAsLong() const
{
    return static_cast<int64_t>(_value);
}

template<typename Number>
float
NumericFieldValue<Number>::getAsFloat() const
{
    return static_cast<float>(_value);
}

template<typename Number>
double
NumericFieldValue<Number>::getAsDouble() const
{
    return static_cast<double>(_value);
}

template<typename Number>
std::string
NumericFieldValue<Number>::getAsString() const
{
    vespalib::asciistream ost;
    if (sizeof(Number) == sizeof(uint8_t)) {
        ost << static_cast<uint32_t>(_value);
    } else {
        ost << _value;
    }
    return ost.str();
}

} // document
