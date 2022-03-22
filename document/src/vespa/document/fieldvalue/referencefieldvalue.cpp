// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "referencefieldvalue.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>
#include <ostream>

using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace document {

ReferenceFieldValue::ReferenceFieldValue()
    : FieldValue(Type::REFERENCE),
      _dataType(nullptr),
      _documentId()
{
}

ReferenceFieldValue::ReferenceFieldValue(const ReferenceDataType& dataType)
    : FieldValue(Type::REFERENCE),
      _dataType(&dataType),
      _documentId()
{
}

ReferenceFieldValue::ReferenceFieldValue(const ReferenceDataType& dataType, const DocumentId& documentId)
    : FieldValue(Type::REFERENCE),
      _dataType(&dataType),
      _documentId(documentId)
{
    requireIdOfMatchingType(_documentId, _dataType->getTargetType());
}

ReferenceFieldValue::~ReferenceFieldValue() = default;

void ReferenceFieldValue::requireIdOfMatchingType(
        const DocumentId& id, const DocumentType& type)
{
    if (id.getDocType() != type.getName()) {
        throw IllegalArgumentException(
                make_string("Can't assign document ID '%s' (of type '%s') to "
                            "reference of document type '%s'",
                            id.toString().c_str(),
                            vespalib::string(id.getDocType()).c_str(),
                            type.getName().c_str()),
                VESPA_STRLOC);
    }
}

FieldValue& ReferenceFieldValue::assign(const FieldValue& rhs) {
    const auto* refValueRhs(dynamic_cast<const ReferenceFieldValue*>(&rhs));
    if (refValueRhs == nullptr) {
        throw IllegalArgumentException(
                make_string("Can't assign field value of type %s to "
                            "a ReferenceFieldValue",
                            rhs.getDataType()->getName().c_str()),
                VESPA_STRLOC);
    }
    if (refValueRhs == this) {
        return *this;
    }
    _documentId = refValueRhs->_documentId;
    _dataType = refValueRhs->_dataType;
    return *this;
}

void ReferenceFieldValue::setDeserializedDocumentId(const DocumentId& id) {
    assert(_dataType != nullptr);
    requireIdOfMatchingType(id, _dataType->getTargetType());
    _documentId = id;
    // Pre-cache GID to ensure it's not attempted lazily initialized later in a racing manner.
    (void) _documentId.getGlobalId();
}

ReferenceFieldValue* ReferenceFieldValue::clone() const {
    assert(_dataType != nullptr);
    if (hasValidDocumentId()) {
        return new ReferenceFieldValue(*_dataType, _documentId);
    } else {
        return new ReferenceFieldValue(*_dataType);
    }
}

int ReferenceFieldValue::compare(const FieldValue& rhs) const {
    const int parentCompare = FieldValue::compare(rhs);
    if (parentCompare != 0) {
        return parentCompare;
    }
    // Type equality is checked by the parent.
    const auto& refValueRhs(dynamic_cast<const ReferenceFieldValue&>(rhs));
    // TODO PERF: DocumentId does currently _not_ expose any methods that
    // cheaply allow an ordering to be established. Only (in)equality operators.
    // IdString::operator== is already implemented in the same way as this, so
    // don't put this code in your inner loops, kids!
    return _documentId.toString().compare(refValueRhs._documentId.toString());
}

void ReferenceFieldValue::print(std::ostream& os, bool verbose, const std::string& indent) const {
    (void) verbose;
    assert(_dataType != nullptr);
    os << indent << "ReferenceFieldValue(" << *_dataType << ", DocumentId(" << _documentId << "))";
}

void ReferenceFieldValue::accept(FieldValueVisitor& visitor) {
    visitor.visit(*this);
}

void ReferenceFieldValue::accept(ConstFieldValueVisitor& visitor) const {
    visitor.visit(*this);
}

} // document
