// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "annotationtype.h"
#include "datatype.h"

namespace document {

class AnnotationReferenceDataType : public DataType {
    const AnnotationType *_type;

public:
    typedef std::shared_ptr<AnnotationReferenceDataType> SP;

    AnnotationReferenceDataType() {}
    AnnotationReferenceDataType(const AnnotationType &type, int id);

    const AnnotationType &getAnnotationType() const;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    AnnotationReferenceDataType *clone() const override;
    std::unique_ptr<FieldValue> createFieldValue() const override;
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;

    DECLARE_IDENTIFIABLE(AnnotationReferenceDataType);
};

}  // namespace document
