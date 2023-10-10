// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <sys/types.h>

namespace document {
class FieldValue;

struct FieldValueWriter {
    virtual ~FieldValueWriter() {}

    virtual void writeFieldValue(const FieldValue &value) = 0;
    virtual void writeSerializedData(const void *buf, size_t length) = 0;
};

}  // namespace document

