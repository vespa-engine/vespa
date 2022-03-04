// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documenttype.h"

namespace document {

/**
 * A ReferenceDataType specifies a particular concrete document type that a
 * ReferenceFieldValue instance binds to.
 */
class ReferenceDataType final : public DataType {
    const DocumentType& _targetDocType;
public:
    ReferenceDataType(const DocumentType& targetDocType, int id);
    ReferenceDataType(const ReferenceDataType &) = delete;
    ReferenceDataType & operator =(const ReferenceDataType &) = delete;
    ~ReferenceDataType() override;

    const DocumentType& getTargetType() const noexcept {
        return _targetDocType;
    }

    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainingFieldName) const override;

    const ReferenceDataType * cast_reference() const noexcept override { return this; }
    bool equals(const DataType &type) const noexcept override;
};

} // document
