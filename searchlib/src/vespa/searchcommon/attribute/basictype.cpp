// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basictype.h"

#include <vespa/vespalib/util/exceptions.h>

namespace search::attribute {

constexpr BasicType::TypeInfo BasicType::_typeTable[BasicType::MAX_TYPE] = {
    {BasicType::NONE, 0, "none"},
    {BasicType::STRING, 0, "string"},
    {BasicType::BOOL, sizeof(int8_t), "bool"},
    {BasicType::INT8, sizeof(int8_t), "int8"},
    {BasicType::INT16, sizeof(int16_t), "int16"},
    {BasicType::INT32, sizeof(int32_t), "int32"},
    {BasicType::INT64, sizeof(int64_t), "int64"},
    {BasicType::FLOAT, sizeof(float), "float"},
    {BasicType::DOUBLE, sizeof(double), "double"},
    {BasicType::PREDICATE, 0, "predicate"},
    {BasicType::TENSOR, 0, "tensor"},
    {BasicType::REFERENCE, 12, "reference"},
    {BasicType::RAW, 0, "raw"}};

consteval bool BasicType::type_table_is_consistent() noexcept {
    for (size_t i = 0; i < MAX_TYPE; ++i) {
        if (_typeTable[i]._type != i || _typeTable[i]._name == nullptr) {
            return false;
        }
    }
    return true;
}

BasicType::Type BasicType::asType(const std::string& t) {
    static_assert(type_table_is_consistent(), "_typeTable must have one entry per Type, in enum order");
    for (size_t i = 0; i < MAX_TYPE; ++i) {
        if (t == _typeTable[i]._name) {
            return _typeTable[i]._type;
        }
    }
    throw vespalib::IllegalStateException(t + " not recognized as valid attribute data type");
    return NONE;
}

bool BasicType::is_integer_type() const noexcept {
    return (_type == INT8) || (_type == INT16) || (_type == INT32) || (_type == INT64);
}

} // namespace search::attribute
