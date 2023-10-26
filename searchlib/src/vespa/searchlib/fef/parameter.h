// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include "fieldinfo.h"
#include "parameterdescriptions.h"

namespace search::fef {

/**
 * This class represents a parameter with type and value.
 * You can use convenience functions to access the parameter value as different types.
 */
class Parameter {
private:
    ParameterType::Enum _type;
    vespalib::string    _stringVal;
    double              _doubleVal;
    int64_t             _intVal;
    const search::fef::FieldInfo * _fieldVal;

public:
    Parameter(ParameterType::Enum type, const vespalib::string & value);
    Parameter & setDouble(double val) { _doubleVal = val; return *this; }
    Parameter & setInteger(int64_t val) { _intVal = val; return *this; }
    Parameter & setField(const search::fef::FieldInfo * val) { _fieldVal = val; return *this; }
    ParameterType::Enum getType() const { return _type; }
    const vespalib::string & getValue() const { return _stringVal; }
    double asDouble() const { return _doubleVal; }
    int64_t asInteger() const { return _intVal; }
    const search::fef::FieldInfo * asField() const { return _fieldVal; }
};

using ParameterList = std::vector<Parameter>;

}
