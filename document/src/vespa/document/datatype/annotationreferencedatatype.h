// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "annotationtype.h"
#include <memory>
#include <vespa/document/datatype/datatype.h>

namespace document {

class AnnotationReferenceDataType : public DataType {
    const AnnotationType *_type;

public:
    typedef std::shared_ptr<AnnotationReferenceDataType> SP;

    AnnotationReferenceDataType() {}
    AnnotationReferenceDataType(const AnnotationType &type, int id);

    const AnnotationType &getAnnotationType() const;
    virtual void print(std::ostream &out, bool verbose,
                       const std::string &indent) const;
    virtual AnnotationReferenceDataType *clone() const;
    virtual std::unique_ptr<FieldValue> createFieldValue() const;
    virtual FieldPath::UP onBuildFieldPath(const vespalib::stringref &remainFieldName) const;

    DECLARE_IDENTIFIABLE(AnnotationReferenceDataType);
};

}  // namespace document

