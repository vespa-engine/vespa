// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationreferencefieldvalue.h"
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

using std::ostream;
using std::string;

namespace document {

int AnnotationReferenceFieldValue::compare(const FieldValue &other) const {
    if (getDataType()->equals(*other.getDataType())) {
        const AnnotationReferenceFieldValue &val(static_cast<const AnnotationReferenceFieldValue &>(other));
        return _annotation_index - val._annotation_index;
    }
    return (getDataType()->getId() - other.getDataType()->getId());
}

void AnnotationReferenceFieldValue::print(ostream &out, bool, const string &) const {
    out << "AnnotationReferenceFieldValue(n)";
}

AnnotationReferenceFieldValue *AnnotationReferenceFieldValue::clone() const {
    return new AnnotationReferenceFieldValue(*this);
}

void AnnotationReferenceFieldValue::printXml(XmlOutputStream &out) const {
    out << _annotation_index;
}

}  // namespace document
