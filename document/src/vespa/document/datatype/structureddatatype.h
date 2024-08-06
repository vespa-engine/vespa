// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    void onBuildFieldPath(FieldPath & path, std::string_view remainFieldName) const override;
protected:
    StructuredDataType(std::string_view name);
    StructuredDataType(std::string_view name, int32_t dataTypeId);

public:
    virtual uint32_t getFieldCount() const noexcept = 0;

    /** @throws FieldNotFoundException if field does not exist. */
    virtual const Field& getField(std::string_view name) const = 0;

    virtual bool hasField(std::string_view name) const noexcept = 0;
    virtual bool hasField(int32_t fieldId) const noexcept = 0;

    virtual Field::Set getFieldSet() const = 0;
    bool isStructured() const noexcept override { return true; }
    bool equals(const DataType& type) const noexcept override;

    static int32_t createId(std::string_view name);
};

}
