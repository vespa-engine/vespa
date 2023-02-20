// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationreferencedatatype.h"
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <ostream>
#include <cassert>

using std::unique_ptr;
using std::ostream;

namespace document {

AnnotationReferenceDataType::AnnotationReferenceDataType(const AnnotationType &type, int id)
    : DataType("annotationreference<" + type.getName() + ">", id),
      _type(&type) {
}

AnnotationReferenceDataType::~AnnotationReferenceDataType() = default;

const AnnotationType &
AnnotationReferenceDataType::getAnnotationType() const {
    assert(_type);
    return *_type;
}

void
AnnotationReferenceDataType::print(ostream &out, bool, const std::string &) const {
    out << "AnnotationReferenceDataType("<< getName() << ", " << getId() << ")";
}

unique_ptr<FieldValue>
AnnotationReferenceDataType::createFieldValue() const {
    return std::make_unique<AnnotationReferenceFieldValue>(*this, 0);
}

void AnnotationReferenceDataType::onBuildFieldPath(FieldPath &, vespalib::stringref) const { }


}  // namespace document
