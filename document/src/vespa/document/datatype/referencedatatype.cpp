// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "referencedatatype.h"
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>

using vespalib::make_string;
using vespalib::IllegalArgumentException;

namespace {
uint32_t
crappyJavaStringHash(std::string_view value) {
    uint32_t h = 0;
    for (uint32_t i = 0; i < value.size(); ++i) {
        h = 31 * h + value[i];
    }
    return h;
}

std::string refTypeName(const std::string& targetDocType) {
    return make_string("Reference<%s>", targetDocType.c_str());
}

}

namespace document {

int32_t ReferenceDataType::makeInternalId(const std::string& targetDocType) {
    return crappyJavaStringHash(refTypeName(targetDocType));
}

ReferenceDataType::ReferenceDataType(const DocumentType& targetDocType, int id)
  : DataType(refTypeName(targetDocType.getName()), id),
    _targetDocType(targetDocType)
{
}

ReferenceDataType::~ReferenceDataType() = default;

std::unique_ptr<FieldValue>
ReferenceDataType::createFieldValue() const {
    return std::make_unique<ReferenceFieldValue>(*this);
}

void
ReferenceDataType::print(std::ostream& os, bool verbose, const std::string& indent) const {
    (void) verbose;
    (void) indent;
    os << "ReferenceDataType(" << _targetDocType.getName() << ", id " << getId() << ')';
}

void
ReferenceDataType::onBuildFieldPath(FieldPath &, std::string_view remainingFieldName) const {
    if ( ! remainingFieldName.empty() ) {
        throw IllegalArgumentException(make_string("Reference data type does not support further field recursion: '%s'",
                                                   std::string(remainingFieldName).c_str()), VESPA_STRLOC);
    }

}

bool
ReferenceDataType::equals(const DataType &rhs) const noexcept {
    const ReferenceDataType *rt = rhs.cast_reference();
    return rt && DataType::equals(rhs) && _targetDocType.equals(rt->_targetDocType);
}

} // document
