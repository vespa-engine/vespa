// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "annotationtype.h"
#include "datatype.h"

namespace document {

class AnnotationReferenceDataType final : public DataType {
    const AnnotationType *_type;

public:
    using SP = std::shared_ptr<AnnotationReferenceDataType>;

    AnnotationReferenceDataType(const AnnotationType &type, int id);
    AnnotationReferenceDataType(const AnnotationReferenceDataType &) = delete;
    AnnotationReferenceDataType & operator=(const AnnotationReferenceDataType &) = delete;
    ~AnnotationReferenceDataType() override;
    const AnnotationType &getAnnotationType() const;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    std::unique_ptr<FieldValue> createFieldValue() const override;
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;
};

}  // namespace document
