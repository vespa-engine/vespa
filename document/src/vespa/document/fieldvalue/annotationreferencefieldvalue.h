// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"
#include <vespa/document/datatype/datatype.h>

namespace document {
class Annotation;
class AnnotationReferenceDataType;

class AnnotationReferenceFieldValue : public FieldValue {
    const DataType *_type;
    int32_t _annotation_index;

public:
    AnnotationReferenceFieldValue(const DataType &type)
        : _type(&type), _annotation_index(0) {}
    AnnotationReferenceFieldValue(const DataType &type,
                                  int32_t annotation_index);
    void setAnnotationIndex(int32_t index) { _annotation_index = index; }

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    int32_t getAnnotationIndex() const { return _annotation_index; }

    virtual int compare(const FieldValue& other) const;
    virtual void print(std::ostream &out, bool verbose,
                       const std::string &indent) const;
    virtual AnnotationReferenceFieldValue *clone() const;
    virtual const DataType *getDataType() const { return _type; }
    virtual void printXml(XmlOutputStream &out) const;
    virtual bool hasChanged() const;
};

}  // namespace document

