// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"
#include <vespa/document/datatype/datatype.h>

namespace document {
class Annotation;
class AnnotationReferenceDataType;

class AnnotationReferenceFieldValue final : public FieldValue {
    const DataType *_type;
    int32_t _annotation_index;

public:
    AnnotationReferenceFieldValue(const DataType &type)
        : AnnotationReferenceFieldValue(type, 0) {}
    AnnotationReferenceFieldValue(const DataType &type, int32_t annotation_index)
        : FieldValue(Type::ANNOTATION_REFERENCE), _type(&type), _annotation_index(annotation_index)
    {}

    void setAnnotationIndex(int32_t index) { _annotation_index = index; }

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    int32_t getAnnotationIndex() const { return _annotation_index; }

    int compare(const FieldValue& other) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    AnnotationReferenceFieldValue *clone() const override;
    const DataType *getDataType() const override { return _type; }
    void printXml(XmlOutputStream &out) const override;
};

}  // namespace document

