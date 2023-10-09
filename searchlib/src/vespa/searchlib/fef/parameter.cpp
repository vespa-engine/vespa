// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parameter.h"

namespace search {
namespace fef {

Parameter::Parameter(ParameterType::Enum type, const vespalib::string & value) :
    _type(type),
    _stringVal(value),
    _doubleVal(0),
    _intVal(0),
    _fieldVal(nullptr)
{
}

} // namespace fef
} // namespace search
