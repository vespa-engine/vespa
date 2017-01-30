// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"
#include "../datatype/referencedatatype.h"
#include "../base/documentid.h"

namespace document {

class ReferenceFieldValue : public FieldValue {
    const ReferenceDataType* _dataType;
    DocumentId _documentId;
public:
    // Empty constructor required for Identifiable.
    ReferenceFieldValue();

    explicit ReferenceFieldValue(const ReferenceDataType& dataType);

    ReferenceFieldValue(const ReferenceDataType& dataType,
                        const DocumentId& documentId);

    ~ReferenceFieldValue();

    ReferenceFieldValue(const ReferenceFieldValue&) = default;
    ReferenceFieldValue& operator=(const ReferenceFieldValue&) = default;

    bool hasValidDocumentId() const noexcept {
        return _documentId.hasDocType();
    }

    // Returned value is only well-defined if hasValidDocumentId() == true.
    const DocumentId& getDocumentId() const noexcept {
        return _documentId;
    }

    const DataType* getDataType() const override { return _dataType; }
    FieldValue& assign(const FieldValue&) override;
    ReferenceFieldValue* clone() const override;
    int compare(const FieldValue&) const override;
    void printXml(XmlOutputStream&) const override { /* Not implemented */ }
    void print(std::ostream&, bool, const std::string&) const override;
    bool hasChanged() const override;
    void accept(FieldValueVisitor&) override;
    void accept(ConstFieldValueVisitor&) const override;

    DECLARE_IDENTIFIABLE(ReferenceFieldValue);
private:
    // Throws vespalib::IllegalArgumentException if  doc type of `id` does not
    // match the name of `type`.
    static void requireIdOfMatchingType(
            const DocumentId& id, const DocumentType& type);
};

} // document
