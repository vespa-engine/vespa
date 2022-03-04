// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::PrimitiveDataType
 * \ingroup datatype
 *
 * \brief Data type describing a primitive.
 *
 * This class describes a primitive data type. Normally you will not access
 * this class directly, you'll use the global datatypes created in DataType,
 * such as DataType::STRING and DataType::INT
 *
 * \todo Add a LiteralDataType subclass, such that this class can become
 *       abstract. Right now you can create a PrimitiveDataType object with
 *       a numeric type id, which is just plain wrong.
 */
#pragma once

#include "datatype.h"

namespace document {

class PrimitiveDataType : public DataType {
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;
public:
    PrimitiveDataType(Type _type);

    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;

    bool isPrimitive() const noexcept override { return true; }
};

}


