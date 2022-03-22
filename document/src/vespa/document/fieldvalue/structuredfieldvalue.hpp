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
    T *t = dynamic_cast<T *>(val.get());
    if (val.get() && !t) {
        throw vespalib::IllegalStateException("Field " + field.toString() + " has unexpected type.", VESPA_STRLOC);
    }
    val.release();
    return std::unique_ptr<T>(t);
}

} // document
