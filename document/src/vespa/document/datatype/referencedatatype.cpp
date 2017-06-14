// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "referencedatatype.h"
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <ostream>

namespace document {

ReferenceDataType::ReferenceDataType(const DocumentType& targetDocType, int id)
    : DataType(vespalib::make_string("Reference<%s>", targetDocType.getName().c_str()), id),
      _targetDocType(targetDocType)
{
}

ReferenceDataType::~ReferenceDataType() {
}

std::unique_ptr<FieldValue> ReferenceDataType::createFieldValue() const {
    return std::make_unique<ReferenceFieldValue>(*this);
}

void ReferenceDataType::print(std::ostream& os, bool verbose, const std::string& indent) const {
    (void) verbose;
    (void) indent;
    os << "ReferenceDataType(" << _targetDocType.getName()
       << ", id " << getId() << ')';
}

ReferenceDataType* ReferenceDataType::clone() const {
    return new ReferenceDataType(_targetDocType, getId());
}

std::unique_ptr<FieldPath> ReferenceDataType::onBuildFieldPath(
        const vespalib::stringref& remainingFieldName) const {
    if (!remainingFieldName.empty()) {
        throw vespalib::IllegalArgumentException(
                vespalib::make_string("Reference data type does not support "
                                      "further field recursion: '%s'",
                                      remainingFieldName.c_str()), VESPA_STRLOC);
    }
    return std::make_unique<FieldPath>();
}

} // document
