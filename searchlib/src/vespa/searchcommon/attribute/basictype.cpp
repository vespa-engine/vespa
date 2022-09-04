// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basictype.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search::attribute {

const BasicType::TypeInfo BasicType::_typeTable[BasicType::MAX_TYPE] = {
    { BasicType::NONE,      0,                "none" },
    { BasicType::STRING,    0,                "string" },
    { BasicType::BOOL,      sizeof(int8_t),   "bool" },
    { BasicType::UINT2,     sizeof(int8_t),   "uint2" },
    { BasicType::UINT4,     sizeof(int8_t),   "uint4" },
    { BasicType::INT8,      sizeof(int8_t),   "int8" },
    { BasicType::INT16,     sizeof(int16_t),  "int16" },
    { BasicType::INT32,     sizeof(int32_t),  "int32" },
    { BasicType::INT64,     sizeof(int64_t),  "int64" },
    { BasicType::FLOAT,     sizeof(float),    "float" },
    { BasicType::DOUBLE,    sizeof(double),   "double" },
    { BasicType::PREDICATE, 0,                "predicate" },
    { BasicType::TENSOR,    0,                "tensor" },
    { BasicType::REFERENCE, 12,               "reference" }
};

BasicType::Type
BasicType::asType(const vespalib::string &t)
{
    for (size_t i(0); i < sizeof(_typeTable)/sizeof(_typeTable[0]); i++) {
        if (t == _typeTable[i]._name) {
            return _typeTable[i]._type;
        }
    }
    throw vespalib::IllegalStateException(t + " not recognized as valid attribute data type");
    return NONE;
}

}
