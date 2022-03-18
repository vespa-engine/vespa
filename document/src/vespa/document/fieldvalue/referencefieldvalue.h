// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/base/documentid.h>

namespace document {

/**
 * A reference field value allows search queries to access fields in other
 * document instances as if they were fields natively stored within the
 * searched document. This allows modelling one-to-many relations such as a
 * parent document with many children containing references back to the parent.
 *
 * Each ReferenceFieldValue may contain a single document ID which specifies the
 * instance the field should refer to. This document ID must have a type
 * matching that of the reference data type of the field itself.
 *
 * Note that references are not polymorphic. This means that if you have a
 * document type "foo" inheriting "bar", you cannot have a reference<bar> field
 * containing a document ID for a "foo" document.
 */
class ReferenceFieldValue final : public FieldValue {
    const ReferenceDataType* _dataType;
    // TODO wrap in std::optional once available.
    DocumentId _documentId;
public:
    // Empty constructor required for Identifiable.
    ReferenceFieldValue();

    explicit ReferenceFieldValue(const ReferenceDataType& dataType);

    ReferenceFieldValue(const ReferenceDataType& dataType,
                        const DocumentId& documentId);

    ~ReferenceFieldValue() override;

    ReferenceFieldValue(const ReferenceFieldValue&) = default;
    ReferenceFieldValue& operator=(const ReferenceFieldValue&) = default;

    bool hasValidDocumentId() const noexcept {
        return _documentId.hasDocType();
    }

    // Returned value is only well-defined if hasValidDocumentId() == true.
    const DocumentId& getDocumentId() const noexcept {
        return _documentId;
    }

    // Should only be called by deserializer code, as it will clear hasChanged.
    // `id` must be a valid document ID and cannot be empty.
    void setDeserializedDocumentId(const DocumentId& id);

    const DataType* getDataType() const override { return _dataType; }
    FieldValue& assign(const FieldValue&) override;
    ReferenceFieldValue* clone() const override;
    int compare(const FieldValue&) const override;
    void printXml(XmlOutputStream&) const override { /* Not implemented */ }
    void print(std::ostream&, bool, const std::string&) const override;
    void accept(FieldValueVisitor&) override;
    void accept(ConstFieldValueVisitor&) const override;
private:
    // Throws vespalib::IllegalArgumentException if  doc type of `id` does not
    // match the name of `type`.
    static void requireIdOfMatchingType(const DocumentId& id, const DocumentType& type);
};

} // document
