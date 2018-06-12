// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    void onBuildFieldPath(FieldPath & path, const vespalib::stringref & remainFieldName) const override;

protected:
    StructuredDataType();
    StructuredDataType(const vespalib::stringref & name);
    StructuredDataType(const vespalib::stringref & name, int32_t dataTypeId);


public:
    virtual uint32_t getFieldCount() const = 0;

    /** @throws FieldNotFoundException if field does not exist. */
    virtual const Field& getField(const vespalib::stringref & name) const = 0;

    virtual bool hasField(const vespalib::stringref & name) const = 0;
    virtual bool hasField(int32_t fieldId) const = 0;

    virtual Field::Set getFieldSet() const = 0;

    virtual StructuredDataType* clone() const override = 0;
    bool operator==(const DataType& type) const override;

    static int32_t createId(const vespalib::stringref &name);

    DECLARE_IDENTIFIABLE_ABSTRACT(StructuredDataType);

};

}
