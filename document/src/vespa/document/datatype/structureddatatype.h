// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::StructuredDataType
 * \ingroup datatype
 *
 * \brief Data type describing common parts for structured datatypes.
 *
 * This class contains common functionality for structured data types, like
 * structs and documents.
 */
#pragma once

#include "datatype.h"
#include <vespa/document/base/field.h>

namespace document {

class StructuredDataType : public DataType {
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;
protected:
    StructuredDataType(vespalib::stringref name);
    StructuredDataType(vespalib::stringref name, int32_t dataTypeId);

public:
    virtual uint32_t getFieldCount() const noexcept = 0;

    /** @throws FieldNotFoundException if field does not exist. */
    virtual const Field& getField(vespalib::stringref name) const = 0;

    virtual bool hasField(vespalib::stringref name) const noexcept = 0;
    virtual bool hasField(int32_t fieldId) const noexcept = 0;

    virtual Field::Set getFieldSet() const = 0;
    bool isStructured() const noexcept override { return true; }
    bool equals(const DataType& type) const noexcept override;

    static int32_t createId(vespalib::stringref name);
};

}
