// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "traversingvisitor.h"

namespace document {
    class DocumentTypeRepo;
    class DocumentType;
}

namespace document::select {

class BodyFieldDetector : public TraversingVisitor
{
    const DocumentTypeRepo &_repo;

    void detectFieldType(const FieldValueNode *expr, const DocumentType &type);

public:
    BodyFieldDetector(const DocumentTypeRepo &repo)
        : _repo(repo), foundBodyField(false), foundHeaderField(false)
    {
    }

    bool foundBodyField;
    bool foundHeaderField;

    void visitDocumentType(const DocType&) override {
            // Need to deserialize header to know document type
        foundHeaderField = true;
    }

    void visitFieldValueNode(const FieldValueNode& expr) override;
};

}
