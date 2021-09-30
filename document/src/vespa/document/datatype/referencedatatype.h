// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documenttype.h"

namespace document {

/**
 * A ReferenceDataType specifies a particular concrete document type that a
 * ReferenceFieldValue instance binds to.
 */
class ReferenceDataType : public DataType {
    const DocumentType& _targetDocType;
public:
    ReferenceDataType(const DocumentType& targetDocType, int id);
    ~ReferenceDataType() override;

    const DocumentType& getTargetType() const noexcept {
        return _targetDocType;
    }

    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    ReferenceDataType* clone() const override;
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainingFieldName) const override;

    bool operator==(const DataType &type) const override;
    bool operator==(const ReferenceDataType& type) const { return operator==(static_cast<const DataType&>(type)); }
};

} // document
