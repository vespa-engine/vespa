// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "referencedatatype.h"
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>

using vespalib::make_string;
using vespalib::IllegalArgumentException;

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

void ReferenceDataType::onBuildFieldPath(FieldPath &, const vespalib::stringref& remainingFieldName) const {
    if ( ! remainingFieldName.empty() ) {
        throw IllegalArgumentException(make_string("Reference data type does not support further field recursion: '%s'",
                                                   vespalib::string(remainingFieldName).c_str()), VESPA_STRLOC);
    }

}

} // document
