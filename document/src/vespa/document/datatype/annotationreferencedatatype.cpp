// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationreferencedatatype.h"
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <ostream>
#include <cassert>

using std::unique_ptr;
using std::ostream;

namespace document {

IMPLEMENT_IDENTIFIABLE(AnnotationReferenceDataType, DataType);

AnnotationReferenceDataType::AnnotationReferenceDataType(
        const AnnotationType &type, int id)
    : DataType("annotationreference<" + type.getName() + ">", id),
      _type(&type) {
}

const AnnotationType &AnnotationReferenceDataType::getAnnotationType() const {
    assert(_type);
    return *_type;
}

void
AnnotationReferenceDataType::print(ostream &out, bool, const std::string &) const {
    out << "AnnotationReferenceDataType("
        << getName() << ", " << getId() << ")";
}

AnnotationReferenceDataType *AnnotationReferenceDataType::clone() const {
    return new AnnotationReferenceDataType(*this);
}

unique_ptr<FieldValue> AnnotationReferenceDataType::createFieldValue() const {
    return FieldValue::UP(new AnnotationReferenceFieldValue(*this, 0));
}

void AnnotationReferenceDataType::onBuildFieldPath(FieldPath &, vespalib::stringref) const { }


}  // namespace document
