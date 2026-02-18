// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationreferencedatatype.h"

#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>

#include <cassert>
#include <ostream>

using std::ostream;
using std::unique_ptr;

namespace document {

AnnotationReferenceDataType::AnnotationReferenceDataType(const AnnotationType& type, int id)
    : DataType("annotationreference<" + type.getName() + ">", id), _type(&type) {}

AnnotationReferenceDataType::~AnnotationReferenceDataType() = default;

const AnnotationType& AnnotationReferenceDataType::getAnnotationType() const {
    assert(_type);
    return *_type;
}

void AnnotationReferenceDataType::print(ostream& out, bool, const std::string&) const {
    out << "AnnotationReferenceDataType(" << getName() << ", " << getId() << ")";
}

unique_ptr<FieldValue> AnnotationReferenceDataType::createFieldValue() const {
    return std::make_unique<AnnotationReferenceFieldValue>(*this, 0);
}

void AnnotationReferenceDataType::onBuildFieldPath(FieldPath&, std::string_view) const {}

} // namespace document
