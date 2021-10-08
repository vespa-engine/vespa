// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "structuredfieldvalue.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/vespalib/util/exceptions.h>

namespace document {

template <typename T>
std::unique_ptr<T>
StructuredFieldValue::getAs(const Field &field) const {
    FieldValue::UP val = getValue(field);
    T *t = Identifiable::cast<T *>(val.get());
    if (val.get() && !t) {
        throw vespalib::IllegalStateException("Field " + field.toString() + " has unexpected type.", VESPA_STRLOC);
    }
    val.release();
    return std::unique_ptr<T>(t);
}

template<typename PrimitiveType>
void
StructuredFieldValue::set(const Field& field, PrimitiveType value)
{
    FieldValue::UP fval(field.getDataType().createFieldValue());
    *fval = value;
    setFieldValue(field, std::move(fval));
}

template<typename PrimitiveType>
void
StructuredFieldValue::set(vespalib::stringref fieldName, PrimitiveType value)
{
    set(getField(fieldName), value);
}

} // document
